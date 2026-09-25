package io.xion.infrastructure.process;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnvFileParserTest {

    @TempDir
    Path temp;

    @Test
    void parsesKeyValueCommentsAndExport() throws Exception {
        Path file = temp.resolve("env");
        Files.writeString(file, """
                # comment
                FOO=bar
                export BAZ=qux
                EMPTY=
                QUOTED="hello world"
                """);
        Map<String, String> env = EnvFileParser.parse(file);
        assertThat(env).containsEntry("FOO", "bar")
                .containsEntry("BAZ", "qux")
                .containsEntry("EMPTY", "")
                .containsEntry("QUOTED", "hello world");
    }

    @Test
    void mergeEnvFilesThenOverrides() {
        Map<String, String> merged = EnvFileParser.merge(
                Map.of("A", "1", "B", "2"),
                Map.of("B", "override", "C", "3"));
        assertThat(merged)
                .containsEntry("A", "1")
                .containsEntry("B", "override")
                .containsEntry("C", "3");
    }

    @Test
    void parseAssignment() {
        assertThat(EnvFileParser.parseAssignment("FOO=bar")).containsEntry("FOO", "bar");
        assertThat(EnvFileParser.parseAssignment("FOO")).containsEntry("FOO", "");
        assertThatThrownBy(() -> EnvFileParser.parseAssignment(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void missingFileThrows() {
        assertThatThrownBy(() -> EnvFileParser.parse(temp.resolve("nope")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("env-file");
    }
}
