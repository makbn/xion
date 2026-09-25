package io.xion.presentation.cli.compat;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DockerCommandTranslatorTest {

    private final DockerCommandTranslator translator = new DockerCommandTranslator();

    @Test
    void translatesDockerRunCoreFlags() {
        var opts = new DockerCommandTranslator.Options();
        opts.allowPartial = true;
        var t = translator.translate(List.of(
                "docker", "run", "-d", "--name", "web",
                "-p", "8080:80", "-v", "/data:/data:ro",
                "--network", "frontend", "--memory", "256m", "--cpus", "1.5",
                "-e", "FOO=1", "nginx:1.25", "-g", "daemon"), opts);
        assertThat(t.xionArgs()).containsExactly(
                "run",
                "--name", "web",
                "-p", "8080:80",
                "-v", "/data:/data:ro",
                "--network", "frontend",
                "--memory", "256m",
                "--cpus", "1.5",
                "--",
                "nginx",
                "-g", "daemon");
        assertThat(t.dropped()).anyMatch(d -> d.contains("-e"));
        assertThat(t.warnings()).isNotEmpty();
        assertThat(t.partial()).isTrue();
    }

    @Test
    void refusesUnsupportedWithoutAllowPartial() {
        assertThatThrownBy(() -> translator.translate(List.of("run", "-e", "A=1", "nginx")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("--allow-partial");
    }

    @Test
    void partialPortsSkipsBareContainerPort() {
        var opts = new DockerCommandTranslator.Options();
        opts.partialPorts = true;
        opts.allowPartial = true;
        var t = translator.translate(List.of("run", "-p", "80", "-p", "9000:90", "/usr/bin/sleep", "1"), opts);
        assertThat(t.xionArgs()).contains("-p", "9000:90");
        assertThat(t.xionArgs()).doesNotContain("80");
        assertThat(t.dropped()).anyMatch(d -> d.contains("-p 80"));
    }

    @Test
    void translatesPsStopLogsNetwork() {
        assertThat(translator.translate(List.of("ps", "-a"), allow()).xionArgs()).containsExactly("ps");
        assertThat(translator.translate(List.of("stop", "web")).xionArgs()).containsExactly("stop", "web");
        assertThat(translator.translate(List.of("logs", "-f", "web"), allow()).xionArgs())
                .containsExactly("logs", "web");
        assertThat(translator.translate(List.of("network", "create", "frontend")).xionArgs())
                .containsExactly("network", "create", "frontend");
        assertThat(translator.translate(List.of("version")).xionArgs()).containsExactly("version");
    }

    @Test
    void acceptsQuotedSingleStringCommand() {
        var t = translator.translate(List.of("docker run --name x /bin/true"), allow());
        assertThat(t.xionArgs()).contains("run", "--name", "x", "--", "/bin/true");
    }

    @Test
    void convertsBindMount() {
        var t = translator.translate(List.of(
                "run", "--mount", "type=bind,source=/h,target=/c,readonly=true", "/bin/true"), allow());
        assertThat(t.xionArgs()).contains("-v", "/h:/c:ro");
    }

    private static DockerCommandTranslator.Options allow() {
        var o = new DockerCommandTranslator.Options();
        o.allowPartial = true;
        o.dropUnsupported = true;
        return o;
    }
}
