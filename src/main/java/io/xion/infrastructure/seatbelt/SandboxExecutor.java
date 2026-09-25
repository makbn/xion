package io.xion.infrastructure.seatbelt;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * OS edge for spawning sandboxed processes. Darwin uses sandbox-exec; Linux CI uses fakes.
 */
public interface SandboxExecutor {

    default SpawnedProcess spawn(Path profileFile, String binary, List<String> args, Path workDir,
                                 Path stdoutLog, Path stderrLog) throws Exception {
        return spawn(profileFile, binary, args, workDir, stdoutLog, stderrLog, Map.of());
    }

    SpawnedProcess spawn(Path profileFile, String binary, List<String> args, Path workDir,
                         Path stdoutLog, Path stderrLog, Map<String, String> env) throws Exception;

    record SpawnedProcess(long pid, Process process) {
    }
}
