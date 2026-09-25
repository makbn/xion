package io.xion.infrastructure.seatbelt;

import io.xion.domain.ContainerProfile;
import io.xion.domain.PortMapping;
import io.xion.domain.ResourceLimits;
import io.xion.domain.VolumeMount;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SeatbeltProfileGeneratorTest {

    @TempDir
    Path temp;

    @Test
    void generatesDenyDefaultWithVolumeAndLoopbackRules() {
        SeatbeltProfileGenerator gen = new SeatbeltProfileGenerator(temp.resolve("profiles").toString());
        ContainerProfile profile = new ContainerProfile(
                "abc123",
                "web",
                "/bin/true",
                List.of(),
                List.of(new VolumeMount("/host/data", "/data", false),
                        new VolumeMount("/host/ro", "/ro", true)),
                List.of(new PortMapping(8080, 80, "tcp")),
                Optional.empty(),
                ResourceLimits.unlimited(),
                temp.resolve("runtime/abc123").toString());

        String sb = gen.generate(profile, 18080);
        assertThat(sb).contains("(deny default)");
        assertThat(sb).contains("(deny network*)");
        assertThat(sb).contains("file-read-write");
        assertThat(sb).contains("/host/data");
        assertThat(sb).contains("/host/ro");
        assertThat(sb).contains("file-read*");
        assertThat(sb).contains("localhost:18080");
        assertThat(sb).contains("127.0.0.1:18080");
    }

    @Test
    void writesProfileFile() throws Exception {
        SeatbeltProfileGenerator gen = new SeatbeltProfileGenerator(temp.resolve("profiles").toString());
        ContainerProfile profile = new ContainerProfile(
                "id1", "n", "/bin/true", List.of(), List.of(), List.of(),
                Optional.empty(), ResourceLimits.unlimited(), temp.toString());
        Path file = gen.writeProfile(profile, 0);
        assertThat(file).exists();
        assertThat(Files.readString(file)).contains("(deny default)");
    }

    @Test
    @Tag("darwin")
    @EnabledOnOs(OS.MAC)
    void sandboxExecAcceptsGeneratedProfile() throws Exception {
        SeatbeltProfileGenerator gen = new SeatbeltProfileGenerator(temp.resolve("profiles").toString());
        // Minimal allow-all-ish profile still with deny default + process-exec for /usr/bin/true
        ContainerProfile profile = new ContainerProfile(
                "darwin-smoke",
                "smoke",
                "/usr/bin/true",
                List.of(),
                List.of(),
                List.of(),
                Optional.empty(),
                ResourceLimits.unlimited(),
                temp.resolve("rt").toString());
        Path sb = gen.writeProfile(profile, 0);
        // Append permissive process/file rules for smoke
        Files.writeString(sb, Files.readString(sb) + """
                (allow file-read*)
                (allow file-write*)
                """);
        Process p = new ProcessBuilder("sandbox-exec", "-f", sb.toString(), "/usr/bin/true").start();
        assertThat(p.waitFor()).isZero();
    }
}
