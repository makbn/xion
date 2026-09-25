package io.xion.infrastructure.seatbelt;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Fake sandbox executor for Linux CI / non-Darwin environments.
 * Runs the binary directly without sandbox-exec.
 */
@ApplicationScoped
@Typed(FakeSandboxExecutor.class)
public class FakeSandboxExecutor implements SandboxExecutor {

    private final AtomicLong nextPid = new AtomicLong(10_000);
    private final List<SpawnedProcess> spawned = new ArrayList<>();
    private final List<Map<String, String>> spawnedEnvs = new ArrayList<>();
    private final List<Path> spawnedWorkDirs = new ArrayList<>();

    @Override
    public SpawnedProcess spawn(Path profileFile, String binary, List<String> args, Path workDir,
                                Path stdoutLog, Path stderrLog, Map<String, String> env) throws Exception {
        Files.createDirectories(workDir);
        if (stdoutLog != null) {
            Files.createDirectories(stdoutLog.getParent());
        }
        if (stderrLog != null) {
            Files.createDirectories(stderrLog.getParent());
        }
        List<String> command = new ArrayList<>();
        command.add(binary);
        if (args != null) {
            command.addAll(args);
        }
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workDir.toFile());
        Map<String, String> envCopy = env == null ? Map.of() : Map.copyOf(env);
        synchronized (spawnedEnvs) {
            spawnedEnvs.add(envCopy);
        }
        synchronized (spawnedWorkDirs) {
            spawnedWorkDirs.add(workDir);
        }
        if (!envCopy.isEmpty()) {
            pb.environment().putAll(envCopy);
        }
        if (stdoutLog != null) {
            pb.redirectOutput(stdoutLog.toFile());
        } else {
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        }
        if (stderrLog != null) {
            pb.redirectError(stderrLog.toFile());
        } else {
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
        }
        Process process = pb.start();
        long pid = process.pid();
        if (pid <= 0) {
            pid = nextPid.incrementAndGet();
        }
        SpawnedProcess result = new SpawnedProcess(pid, process);
        synchronized (spawned) {
            spawned.add(result);
        }
        return result;
    }

    public List<SpawnedProcess> spawned() {
        synchronized (spawned) {
            return List.copyOf(spawned);
        }
    }

    public List<Map<String, String>> spawnedEnvs() {
        synchronized (spawnedEnvs) {
            return List.copyOf(spawnedEnvs);
        }
    }

    public Map<String, String> lastEnv() {
        synchronized (spawnedEnvs) {
            return spawnedEnvs.isEmpty() ? Map.of() : spawnedEnvs.getLast();
        }
    }

    public List<Path> spawnedWorkDirs() {
        synchronized (spawnedWorkDirs) {
            return List.copyOf(spawnedWorkDirs);
        }
    }

    public Path lastWorkDir() {
        synchronized (spawnedWorkDirs) {
            return spawnedWorkDirs.isEmpty() ? null : spawnedWorkDirs.getLast();
        }
    }

    public void clear() {
        synchronized (spawned) {
            spawned.clear();
        }
        synchronized (spawnedEnvs) {
            spawnedEnvs.clear();
        }
        synchronized (spawnedWorkDirs) {
            spawnedWorkDirs.clear();
        }
    }
}
