package io.xion.infrastructure.process;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Pidfile + self-reexec helpers so the daemon can detach like dockerd/podman.
 */
public final class DaemonProcessSupport {

    public static final String FOREGROUND_FLAG = "--foreground";
    public static final String FOREGROUND_FLAG_SHORT = "-f";

    private DaemonProcessSupport() {
    }

    public static Path pidFile(Path runtimeDir) {
        return runtimeDir.resolve("xion.pid");
    }

    public static Path logFile(Path runtimeDir) {
        return runtimeDir.resolve("daemon.log");
    }

    public static void writePid(Path runtimeDir, long pid) throws IOException {
        Files.createDirectories(runtimeDir);
        Files.writeString(pidFile(runtimeDir), Long.toString(pid) + "\n", StandardCharsets.UTF_8);
    }

    public static Optional<Long> readPid(Path runtimeDir) {
        Path file = pidFile(runtimeDir);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            String raw = Files.readString(file, StandardCharsets.UTF_8).trim();
            if (raw.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(Long.parseLong(raw));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public static void clearPid(Path runtimeDir) {
        try {
            Files.deleteIfExists(pidFile(runtimeDir));
        } catch (IOException ignored) {
            // best-effort
        }
    }

    public static boolean isAlive(long pid) {
        return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
    }

    /**
     * True when a live process owns the pidfile (stale pidfiles return false and are cleared).
     */
    public static Optional<Long> livingDaemonPid(Path runtimeDir) {
        Optional<Long> pid = readPid(runtimeDir);
        if (pid.isEmpty()) {
            return Optional.empty();
        }
        if (!isAlive(pid.get())) {
            clearPid(runtimeDir);
            return Optional.empty();
        }
        return pid;
    }

    /**
     * Rebuild {@code <this-jvm-or-native> daemon start --foreground} from the current process argv.
     */
    public static List<String> foregroundRelaunchCommand() {
        ProcessHandle.Info info = ProcessHandle.current().info();
        String executable = info.command()
                .orElseThrow(() -> new IllegalStateException(
                        "Cannot resolve current executable to relaunch the daemon"));
        List<String> cmd = new ArrayList<>();
        cmd.add(executable);
        String[] args = info.arguments().orElse(new String[0]);
        for (String arg : args) {
            if ("daemon".equals(arg)) {
                break;
            }
            cmd.add(arg);
        }
        cmd.add("daemon");
        cmd.add("start");
        cmd.add(FOREGROUND_FLAG);
        return cmd;
    }

    /**
     * Spawn a detached daemon child (nohup/setsid when available), redirecting logs to {@code daemon.log}.
     *
     * @return child pid
     */
    public static long spawnDetached(Path runtimeDir) throws IOException {
        Files.createDirectories(runtimeDir);
        Path log = logFile(runtimeDir);
        List<String> child = foregroundRelaunchCommand();
        List<String> cmd = wrapDetached(child);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(runtimeDir.toFile());
        pb.redirectInput(ProcessBuilder.Redirect.DISCARD);
        pb.redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile()));
        pb.redirectError(ProcessBuilder.Redirect.appendTo(log.toFile()));
        // Do not inherit the parent's environment wholesale beyond ProcessBuilder defaults;
        // child needs PATH/HOME for a normal Quarkus start.
        Process process = pb.start();
        long pid = process.pid();
        writePid(runtimeDir, pid);
        return pid;
    }

    static List<String> wrapDetached(List<String> childCmd) {
        List<String> wrapped = new ArrayList<>();
        if (isUnix()) {
            if (Files.isExecutable(Path.of("/usr/bin/setsid"))) {
                wrapped.add("/usr/bin/setsid");
            } else if (Files.isExecutable(Path.of("/usr/bin/nohup"))) {
                wrapped.add("/usr/bin/nohup");
            }
        }
        wrapped.addAll(childCmd);
        return wrapped;
    }

    public static boolean waitUntil(java.util.function.BooleanSupplier ready, long timeoutMs)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        while (System.nanoTime() < deadline) {
            if (ready.getAsBoolean()) {
                return true;
            }
            Thread.sleep(50);
        }
        return ready.getAsBoolean();
    }

    public static boolean stopPid(long pid, long gracefulMs) throws InterruptedException {
        Optional<ProcessHandle> handle = ProcessHandle.of(pid);
        if (handle.isEmpty() || !handle.get().isAlive()) {
            return true;
        }
        ProcessHandle ph = handle.get();
        ph.destroy();
        try {
            ph.onExit().get(gracefulMs, TimeUnit.MILLISECONDS);
            return true;
        } catch (java.util.concurrent.TimeoutException e) {
            ph.destroyForcibly();
            try {
                ph.onExit().get(3, TimeUnit.SECONDS);
            } catch (Exception ignored) {
                // fall through
            }
            return !ph.isAlive();
        } catch (java.util.concurrent.ExecutionException e) {
            return !ph.isAlive();
        }
    }

    /**
     * Remove a stale UDS when the pidfile is missing/dead so clients do not hit a dead sock (CF 502).
     *
     * @return true if a stale socket file was deleted
     */
    public static boolean clearStaleSocket(Path socketPath, Path runtimeDir) throws IOException {
        if (!Files.exists(socketPath)) {
            return false;
        }
        Optional<Long> live = livingDaemonPid(runtimeDir);
        if (live.isPresent()) {
            return false;
        }
        Files.deleteIfExists(socketPath);
        return true;
    }

    private static boolean isUnix() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return os.contains("mac") || os.contains("nux") || os.contains("bsd");
    }
}
