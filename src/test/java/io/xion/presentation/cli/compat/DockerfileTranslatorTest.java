package io.xion.presentation.cli.compat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DockerfileTranslatorTest {

    private final DockerfileParser parser = new DockerfileParser();
    private final DockerfileTranslator translator = new DockerfileTranslator();

    @TempDir
    Path temp;

    @Test
    void parsesExecFormEntrypointAndCmd() {
        var parsed = parser.parseLines(Path.of("Dockerfile"), List.of(
                "FROM alpine",
                "WORKDIR /app",
                "EXPOSE 8080",
                "VOLUME /data",
                "ENV FOO=bar",
                "ENTRYPOINT [\"/usr/bin/myapp\"]",
                "CMD [\"--serve\", \"--port\", \"8080\"]"));
        assertThat(parsed.executableArgv())
                .containsExactly("/usr/bin/myapp", "--serve", "--port", "8080");
        assertThat(parsed.exposePorts()).containsExactly(8080);
        assertThat(parsed.volumes()).containsExactly("/data");
        assertThat(parsed.workdir()).contains("/app");
        assertThat(parsed.envAssignments()).contains("FOO=bar");
    }

    @Test
    void shellFormCmdBecomesShC() {
        var parsed = parser.parseLines(Path.of("Dockerfile"), List.of(
                "CMD python -m http.server 8000"));
        assertThat(parsed.executableArgv())
                .containsExactly("/bin/sh", "-c", "python -m http.server 8000");
    }

    @Test
    void translatesDockerfileToXionRun(@TempDir Path dir) throws Exception {
        Path df = dir.resolve("Dockerfile");
        Files.writeString(df, """
                FROM eclipse-temurin:25
                RUN ./gradlew build
                WORKDIR /opt/app
                EXPOSE 8080 8443/tcp
                VOLUME /var/log/app
                ENV APP_ENV=prod
                USER nobody
                ENTRYPOINT ["/usr/bin/java"]
                CMD ["-jar", "/opt/app/app.jar"]
                """);
        var opts = new DockerfileTranslator.Options();
        opts.name = "api";
        opts.publishExpose = true;
        opts.volumeHostRoot = "/srv/xion";
        opts.honorWorkdir = true;
        opts.commandOptions.allowPartial = true;

        var t = translator.translate(df, opts);
        assertThat(t.xionArgs()).contains("run", "--name", "api");
        assertThat(t.xionArgs()).contains("-p", "8080:8080", "-p", "8443:8443");
        assertThat(t.xionArgs()).contains("-v", "/srv/xion/var/log/app:/var/log/app");
        // workdir wrap → /bin/sh -c 'cd … && exec …'
        assertThat(t.xionArgs()).contains("/bin/sh");
        assertThat(String.join(" ", t.xionArgs())).contains("cd /opt/app");
        assertThat(String.join(" ", t.xionArgs())).contains("/usr/bin/java");
        assertThat(t.xionArgs()).contains("-e", "APP_ENV=prod");
        assertThat(t.dropped()).noneMatch(d -> d.startsWith("ENV "));
        assertThat(t.dropped()).anyMatch(d -> d.startsWith("USER"));
        assertThat(t.warnings()).anyMatch(w -> w.contains("build-only"));
        assertThat(t.summary()).contains("Dockerfile");
    }

    @Test
    void failsWithoutEntrypointOrCmd(@TempDir Path dir) throws Exception {
        Path df = dir.resolve("Dockerfile");
        Files.writeString(df, "FROM alpine\nRUN echo hi\n");
        assertThatThrownBy(() -> translator.translate(df, new DockerfileTranslator.Options()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ENTRYPOINT");
    }
}
