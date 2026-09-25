package io.xion.infrastructure.resources;

import io.xion.domain.ResourceLimits;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;
import org.jboss.logging.Logger;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * FFM (Panama) bindings for {@code setrlimit} + macOS {@code taskpolicy} wrapping for --cpus.
 */
@ApplicationScoped
@Typed(DarwinResourceGovernor.class)
public class DarwinResourceGovernor implements ResourceGovernor {

    private static final Logger LOG = Logger.getLogger(DarwinResourceGovernor.class);

    // Darwin RLIMIT_AS / RLIMIT_RSS / RLIMIT_CPU — values match macOS sys/resource.h
    static final int RLIMIT_CPU = 0;
    static final int RLIMIT_AS = 5;
    static final int RLIMIT_RSS = 5;

    private final MethodHandle setrlimit;

    public DarwinResourceGovernor() {
        MethodHandle handle = null;
        try {
            Linker linker = Linker.nativeLinker();
            SymbolLookup lookup = linker.defaultLookup();
            Optional<MemorySegment> symbol = lookup.find("setrlimit");
            if (symbol.isPresent()) {
                handle = linker.downcallHandle(
                        symbol.get(),
                        FunctionDescriptor.of(
                                ValueLayout.JAVA_INT,
                                ValueLayout.JAVA_INT,
                                ValueLayout.ADDRESS));
            }
        } catch (Throwable t) {
            LOG.debug("setrlimit FFM binding unavailable", t);
        }
        this.setrlimit = handle;
    }

    @Override
    public void apply(ResourceLimits limits) {
        if (!limits.isLimited()) {
            return;
        }
        limits.memoryBytes().ifPresent(bytes -> {
            try {
                applyLimit(RLIMIT_AS, bytes);
                applyLimit(RLIMIT_RSS, bytes);
            } catch (Throwable t) {
                throw new IllegalStateException("Failed to apply memory rlimit", t);
            }
        });
        limits.cpus().ifPresent(cpus -> {
            // Soft CPU-time budget proportional to requested CPUs (seconds of CPU time).
            long cpuSeconds = Math.max(1L, (long) Math.ceil(cpus * 3600));
            try {
                applyLimit(RLIMIT_CPU, cpuSeconds);
            } catch (Throwable t) {
                LOG.warn("Failed to apply CPU rlimit", t);
            }
        });
    }

    private void applyLimit(int resource, long value) throws Throwable {
        if (setrlimit == null) {
            LOG.debugf("setrlimit unavailable; skipping resource=%d value=%d", resource, value);
            return;
        }
        try (Arena arena = Arena.ofConfined()) {
            // struct rlimit { rlim_t rlim_cur; rlim_t rlim_max; } — rlim_t is 64-bit on Darwin
            MemorySegment rlimit = arena.allocate(16);
            rlimit.set(ValueLayout.JAVA_LONG, 0, value);
            rlimit.set(ValueLayout.JAVA_LONG, 8, value);
            int rc = (int) setrlimit.invoke(resource, rlimit);
            if (rc != 0) {
                LOG.warnf("setrlimit(%d, %d) returned %d", resource, value, rc);
            }
        }
    }

    @Override
    public List<String> wrapCommand(List<String> command, ResourceLimits limits) {
        apply(limits);
        if (limits.cpus().isEmpty()) {
            return command;
        }
        // macOS taskpolicy -c utility biases scheduling for background/utility tier when cpus requested
        List<String> wrapped = new ArrayList<>();
        wrapped.add("taskpolicy");
        wrapped.add("-c");
        wrapped.add("utility");
        wrapped.addAll(command);
        return wrapped;
    }
}
