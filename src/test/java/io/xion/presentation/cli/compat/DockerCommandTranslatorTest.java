package io.xion.presentation.cli.compat;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DockerCommandTranslatorTest {

    private final DockerCommandTranslator translator = new DockerCommandTranslator();

    @Test
    void translatesDockerRunCoreFlags() {
        // -e is supported — no allowPartial required solely for env
        var t = translator.translate(List.of(
                "docker", "run", "-d", "--name", "web",
                "-p", "8080:80", "-v", "/data:/data:ro",
                "--network", "frontend", "--memory", "256m", "--cpus", "1.5",
                "-e", "FOO=1", "nginx:1.25", "-g", "daemon"));
        assertThat(t.xionArgs()).containsExactly(
                "run",
                "--name", "web",
                "-p", "8080:80",
                "-v", "/data:/data:ro",
                "--network", "frontend",
                "--memory", "256m",
                "--cpus", "1.5",
                "-e", "FOO=1",
                "--",
                "nginx",
                "-g", "daemon");
        assertThat(t.dropped()).noneMatch(d -> d.contains("-e"));
        assertThat(t.warnings()).isNotEmpty(); // -d detach + image tag strip
        assertThat(t.partial()).isTrue();
    }

    @Test
    void translatesWorkdirRmRestartEnvFile() {
        var t = translator.translate(List.of(
                "run", "-w", "/app", "--rm", "--restart", "on-failure:3",
                "--env-file", "/tmp/env", "/usr/bin/sleep", "1"));
        assertThat(t.xionArgs()).contains("-w", "/app", "--rm", "--restart", "on-failure:3",
                "--env-file", "/tmp/env", "--", "/usr/bin/sleep", "1");
        assertThat(t.dropped()).isEmpty();
    }

    @Test
    void refusesUnsupportedWithoutAllowPartial() {
        assertThatThrownBy(() -> translator.translate(List.of("run", "-u", "nobody", "nginx")))
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
        assertThat(translator.translate(List.of("ps", "-a")).xionArgs()).containsExactly("ps", "-a");
        assertThat(translator.translate(List.of("ps"), allow()).xionArgs()).containsExactly("ps");
        assertThat(translator.translate(List.of("ps", "-a"), allow()).xionArgs()).containsExactly("ps", "-a");
        assertThat(translator.translate(List.of("stop", "web")).xionArgs()).containsExactly("stop", "web");
        assertThat(translator.translate(List.of("logs", "-f", "--tail", "20", "web")).xionArgs())
                .containsExactly("logs", "-f", "--tail", "20", "web");
        assertThat(translator.translate(List.of("network", "create", "frontend")).xionArgs())
                .containsExactly("network", "create", "frontend");
        assertThat(translator.translate(List.of("network", "ls")).xionArgs())
                .containsExactly("network", "ls");
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
