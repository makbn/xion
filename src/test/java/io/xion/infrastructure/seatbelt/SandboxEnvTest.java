package io.xion.infrastructure.seatbelt;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SandboxEnvTest {

    @Test
    void forcesTmpdirOverHostVarFolders() {
        Map<String, String> host = Map.of(
                "PATH", "/usr/bin",
                "TMPDIR", "/var/folders/xx/T",
                "HOME", "/Users/me",
                "SECRET", "should-not-leak");
        Map<String, String> curated = SandboxEnv.curated(host, Map.of("FOO", "bar"));
        assertThat(curated).containsEntry("TMPDIR", "/tmp");
        assertThat(curated).containsEntry("TMP", "/tmp");
        assertThat(curated).containsEntry("TEMP", "/tmp");
        assertThat(curated).containsEntry("PATH", "/usr/bin");
        assertThat(curated).containsEntry("HOME", "/Users/me");
        assertThat(curated).containsEntry("FOO", "bar");
        assertThat(curated).doesNotContainKey("SECRET");
        assertThat(curated.get("TMPDIR")).doesNotContain("/var/folders");
    }

    @Test
    void overridesCannotReintroduceVarFolders() {
        Map<String, String> curated = SandboxEnv.curated(
                Map.of("PATH", "/bin"),
                Map.of("TMPDIR", "/var/folders/evil/T"));
        assertThat(curated).containsEntry("TMPDIR", "/tmp");
    }
}
