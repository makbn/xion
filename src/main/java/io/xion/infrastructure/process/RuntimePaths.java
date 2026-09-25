package io.xion.infrastructure.process;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.file.Path;

@ApplicationScoped
public class RuntimePaths {

    private final Path runtimeDir;
    private final Path profilesDir;

    public RuntimePaths(
            @ConfigProperty(name = "xion.runtime-dir") String runtimeDir,
            @ConfigProperty(name = "xion.profiles-dir") String profilesDir) {
        this.runtimeDir = Path.of(runtimeDir);
        this.profilesDir = Path.of(profilesDir);
    }

    public Path runtimeDir() {
        return runtimeDir;
    }

    public Path profilesDir() {
        return profilesDir;
    }

    public Path containerDir(String id) {
        return runtimeDir.resolve("containers").resolve(id);
    }

    public Path stdoutLog(String id) {
        return containerDir(id).resolve("stdout.log");
    }

    public Path stderrLog(String id) {
        return containerDir(id).resolve("stderr.log");
    }
}
