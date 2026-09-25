package io.xion.infrastructure.seatbelt;

import java.nio.file.Path;
import java.util.List;

/**
 * OS edge for spawning sandboxed processes. Darwin uses sandbox-exec; Linux CI uses fakes.
 */
public interface SandboxExecutor {

    SpawnedProcess spawn(Path profileFile, String binary, List<String> args, Path workDir,
                         Path stdoutLog, Path stderrLog) throws Exception;

    record SpawnedProcess(long pid, Process process) {
    }
}
