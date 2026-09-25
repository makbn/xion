package io.xion.infrastructure.seatbelt;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Real macOS Seatbelt executor: {@code sandbox-exec -f <profile> <binary> …}.
 */
@ApplicationScoped
@Typed(DarwinSandboxExecutor.class)
public class DarwinSandboxExecutor implements SandboxExecutor {

    @Override
    public SpawnedProcess spawn(Path profileFile, String binary, List<String> args, Path workDir,
                                Path stdoutLog, Path stderrLog) throws Exception {
        Files.createDirectories(workDir);
        if (stdoutLog != null) {
            Files.createDirectories(stdoutLog.getParent());
        }
        if (stderrLog != null) {
            Files.createDirectories(stderrLog.getParent());
        }
        List<String> command = new ArrayList<>();
        command.add("sandbox-exec");
        command.add("-f");
        command.add(profileFile.toAbsolutePath().toString());
        command.add(binary);
        if (args != null) {
            command.addAll(args);
        }
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workDir.toFile());
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
        return new SpawnedProcess(process.pid(), process);
    }
}
