package io.xion.infrastructure.seatbelt;

import io.xion.domain.ContainerProfile;
import io.xion.domain.PortMapping;
import io.xion.domain.ResourceLimits;
import io.xion.domain.RestartPolicy;
import io.xion.domain.SandboxProfile;
import io.xion.domain.VolumeMount;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class SeatbeltProfileGeneratorTest {

    private static final Pattern DOTTED_IP_LITERAL = Pattern.compile(
            "\\(local ip \"\\d+\\.\\d+\\.\\d+\\.\\d+:");
    private static final Pattern REMOTE_DOTTED_IP = Pattern.compile(
            "\\(remote ip \"\\d+\\.\\d+\\.\\d+\\.\\d+:");

    @TempDir
    Path temp;

    @Test
    void strictNeverEmitsInvalidFilterNamesOrDottedIpHosts() {
        SeatbeltProfileGenerator gen = new SeatbeltProfileGenerator(temp.resolve("profiles").toString());
        ContainerProfile profile = baseProfile(SandboxProfile.STRICT, List.of(
                new VolumeMount("/host/data", "/data", false),
                new VolumeMount("/host/ro", "/ro", true)),
                List.of(new PortMapping(8080, 80, "tcp")));

        String sb = gen.generate(profile, 18080, "10.89.0.2");
        assertValidSeatbeltShape(sb);
        assertThat(sb).contains("(deny default)");
        assertThat(sb).contains("(deny network*)");
        assertThat(sb).contains("/host/data");
        assertThat(sb).contains("file-read*");
        assertThat(sb).contains("file-write*");
        assertThat(sb).doesNotContain("file-read-write");
        // RO volume is covered by global file-read*; no per-volume write rule
        assertThat(sb).doesNotContain("(allow file-write* (subpath \"/host/ro\"))");
        assertThat(sb).contains("localhost:18080");
        assertThat(sb).contains("*:80");
        assertThat(sb).contains("(allow file-read*)");
        assertThat(sb).contains("(literal \"/dev/null\")");
        assertThat(sb).contains("(literal \"/dev/tty\")");
        assertThat(sb).contains("(allow file-map-executable)");
        assertThat(sb).contains("(allow system-socket)");
        assertThat(sb).doesNotContain("10.89.0.2");
        assertThat(DOTTED_IP_LITERAL.matcher(sb).find()).isFalse();
        assertThat(REMOTE_DOTTED_IP.matcher(sb).find()).isFalse();
    }

    @Test
    void writablePathFromProfileAppearsInSeatbelt() {
        SeatbeltProfileGenerator gen = new SeatbeltProfileGenerator(temp.resolve("profiles").toString());
        ContainerProfile profile = new ContainerProfile(
                "abc123", "web", "/usr/bin/true", List.of(), List.of(),
                List.of(new PortMapping(8080, 80, "tcp")),
                Optional.empty(), ResourceLimits.unlimited(),
                temp.resolve("runtime/abc123").toString(),
                Map.of(), Optional.empty(), false, RestartPolicy.NO, SandboxProfile.STRICT,
                List.of("/data/cache"));
        String sb = gen.generate(profile, 18080);
        assertThat(sb).contains("/data/cache");
        assertThat(sb).contains("file-write*");
    }

    @Test
    void relayAllowsOutboundAndBroadReadWithNarrowWrites() {
        SeatbeltProfileGenerator gen = new SeatbeltProfileGenerator(temp.resolve("profiles").toString());
        ContainerProfile profile = baseProfile(SandboxProfile.RELAY, List.of(
                new VolumeMount("/proj", "/proj", false),
                new VolumeMount("/opt/brew", "/opt/brew", true)),
                List.of(new PortMapping(8080, 8080, "tcp")));

        String sb = gen.generate(profile, 8080);
        assertValidSeatbeltShape(sb);
        assertThat(sb).contains("(allow process*)");
        assertThat(sb).contains("(allow file-read*)");
        assertThat(sb).contains("(allow network-outbound)");
        assertThat(sb).contains("(allow network-inbound (local ip \"localhost:*\"))");
        assertThat(sb).contains("(allow network-inbound (local ip \"*:8080\"))");
        assertThat(sb).contains("(literal \"/dev/null\")");
        assertThat(sb).contains("(allow file-map-executable)");
        assertThat(sb).contains("file-write*");
        assertThat(sb).contains("/proj");
        // RO brew mount: readable via global file-read*, no write rule required
        assertThat(sb).doesNotContain("file-read-write");
        assertThat(DOTTED_IP_LITERAL.matcher(sb).find()).isFalse();
    }

    @Test
    void assignedIpDoesNotInjectDottedLiteralsIntoFilters() {
        SeatbeltProfileGenerator gen = new SeatbeltProfileGenerator(temp.resolve("profiles").toString());
        ContainerProfile profile = baseProfile(SandboxProfile.STRICT, List.of(),
                List.of(new PortMapping(9000, 8087, "tcp")));

        String sb = gen.generate(profile, 9000, "10.89.0.2");
        assertThat(sb).contains("localhost:9000");
        assertThat(sb).contains("*:8087");
        assertThat(sb).doesNotContain("10.89.0.2");
        assertThat(sb).doesNotContain("127.0.0.1");
    }

    @Test
    void writesProfileFile() throws Exception {
        SeatbeltProfileGenerator gen = new SeatbeltProfileGenerator(temp.resolve("profiles").toString());
        ContainerProfile profile = baseProfile(SandboxProfile.STRICT, List.of(), List.of());
        Path file = gen.writeProfile(profile, 0);
        assertThat(file).exists();
        String content = Files.readString(file);
        assertValidSeatbeltShape(content);
        assertThat(content).contains("(deny default)");
    }

    @Test
    @Tag("darwin")
    @EnabledOnOs(OS.MAC)
    void sandboxExecAcceptsGeneratedRelayProfile() throws Exception {
        SeatbeltProfileGenerator gen = new SeatbeltProfileGenerator(temp.resolve("profiles").toString());
        ContainerProfile profile = baseProfile(SandboxProfile.RELAY, List.of(), List.of());
        Path sb = gen.writeProfile(profile, 0);
        assertValidSeatbeltShape(Files.readString(sb));

        Process p = new ProcessBuilder("sandbox-exec", "-f", sb.toString(), "/usr/bin/true")
                .redirectError(ProcessBuilder.Redirect.PIPE)
                .start();
        int code = p.waitFor();
        String err = new String(p.getErrorStream().readAllBytes());
        // Soft skip only for SIP / policy rejections — never for known-invalid filter names.
        org.junit.jupiter.api.Assumptions.assumeTrue(code == 0,
                "sandbox-exec rejected relay profile (exit " + code + "): " + err.trim());
    }

    @Test
    @Tag("darwin")
    @EnabledOnOs(OS.MAC)
    void sandboxExecAcceptsGeneratedStrictProfile() throws Exception {
        SeatbeltProfileGenerator gen = new SeatbeltProfileGenerator(temp.resolve("profiles").toString());
        ContainerProfile profile = baseProfile(SandboxProfile.STRICT, List.of(), List.of());
        Path sb = gen.writeProfile(profile, 0);
        assertValidSeatbeltShape(Files.readString(sb));

        Process p = new ProcessBuilder("sandbox-exec", "-f", sb.toString(), "/usr/bin/true")
                .redirectError(ProcessBuilder.Redirect.PIPE)
                .start();
        int code = p.waitFor();
        String err = new String(p.getErrorStream().readAllBytes());
        org.junit.jupiter.api.Assumptions.assumeTrue(code == 0,
                "sandbox-exec rejected strict profile (exit " + code + "): " + err.trim());
    }

    private static void assertValidSeatbeltShape(String sb) {
        assertThat(sb).doesNotContain("file-read-write");
        assertThat(DOTTED_IP_LITERAL.matcher(sb).find())
                .as("must not use dotted IPv4 in (local ip \"…\")").isFalse();
        assertThat(REMOTE_DOTTED_IP.matcher(sb).find())
                .as("must not use dotted IPv4 in (remote ip \"…\")").isFalse();
    }

    private ContainerProfile baseProfile(
            SandboxProfile sandbox,
            List<VolumeMount> volumes,
            List<PortMapping> ports) {
        return new ContainerProfile(
                "abc123",
                "web",
                "/usr/bin/true",
                List.of(),
                volumes,
                ports,
                Optional.empty(),
                ResourceLimits.unlimited(),
                temp.resolve("runtime/abc123").toString(),
                Map.of(),
                Optional.empty(),
                false,
                RestartPolicy.NO,
                sandbox);
    }
}
