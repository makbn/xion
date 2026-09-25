package io.xion.infrastructure.resources;

import io.xion.domain.ResourceLimits;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;
import org.jboss.logging.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Child-scoped resource limits on Darwin.
 * <p>
 * Never calls {@code setrlimit} on the daemon process (that poisoned every later container).
 * Memory is applied via a {@code /bin/sh} + {@code ulimit} wrapper before {@code exec};
 * CPU scheduling via {@code /usr/sbin/taskpolicy}.
 */
@ApplicationScoped
@Typed(DarwinResourceGovernor.class)
public class DarwinResourceGovernor implements ResourceGovernor {

    private static final Logger LOG = Logger.getLogger(DarwinResourceGovernor.class);

    static final String TASKPOLICY = resolveTaskpolicy();

    private static String resolveTaskpolicy() {
        Path sbin = Path.of("/usr/sbin/taskpolicy");
        if (Files.isExecutable(sbin)) {
            return sbin.toString();
        }
        Path bin = Path.of("/usr/bin/taskpolicy");
        if (Files.isExecutable(bin)) {
            return bin.toString();
        }
        return "/usr/sbin/taskpolicy";
    }

    /**
     * No-op on purpose: must not {@code setrlimit} the long-lived daemon.
     * Limits are applied only in {@link #wrapCommand}.
     */
    @Override
    public void apply(ResourceLimits limits) {
        if (limits != null && limits.isLimited()) {
            LOG.debugf("Deferring resource limits to child wrap (memory=%s cpus=%s); not applying to daemon",
                    limits.memoryBytes(), limits.cpus());
        }
    }

    @Override
    public List<String> wrapCommand(List<String> command, ResourceLimits limits) {
        List<String> cmd = List.copyOf(command);
        if (limits != null && limits.memoryBytes().isPresent()) {
            long kb = Math.max(1L, limits.memoryBytes().get() / 1024L);
            // Child-scoped ulimit then exec — never touches the daemon's rlimits.
            List<String> sh = new ArrayList<>();
            sh.add("/bin/sh");
            sh.add("-c");
            sh.add("ulimit -S -d " + kb + " 2>/dev/null; ulimit -S -v " + kb + " 2>/dev/null; exec \"$@\"");
            sh.add("xion-rlimit");
            sh.addAll(cmd);
            cmd = List.copyOf(sh);
        }
        if (limits != null && limits.cpus().isPresent()) {
            List<String> wrapped = new ArrayList<>();
            wrapped.add(TASKPOLICY);
            wrapped.add("-c");
            wrapped.add("utility");
            wrapped.addAll(cmd);
            return wrapped;
        }
        return cmd;
    }
}
