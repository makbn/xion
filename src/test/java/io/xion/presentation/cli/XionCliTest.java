package io.xion.presentation.cli;

import io.quarkus.test.junit.main.Launch;
import io.quarkus.test.junit.main.LaunchResult;
import io.quarkus.test.junit.main.QuarkusMainTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusMainTest
class XionCliTest {

    @Test
    @Launch({"version"})
    void versionPrints(LaunchResult result) {
        assertThat(result.getOutput()).contains("xion ");
        assertThat(result.exitCode()).isZero();
    }

    @Test
    @Launch({"--help"})
    void helpListsSubcommandsAndDockerCompat(LaunchResult result) {
        String out = result.getOutput();
        assertThat(out).contains("daemon");
        assertThat(out).contains("run");
        assertThat(out).contains("ps");
        assertThat(out).contains("version");
        assertThat(out).contains("docker");
        assertThat(out).contains("Coming from Docker");
        assertThat(out).contains("Examples:");
    }

    @Test
    @Launch({"run", "--help"})
    void runHelpIsDetailed(LaunchResult result) {
        String out = result.getOutput();
        assertThat(out).contains("Seatbelt");
        assertThat(out).contains("--memory");
        assertThat(out).contains("--publish");
        assertThat(out).contains("Examples:");
    }

    @Test
    @Launch({"docker", "--help"})
    void dockerCompatHelpDocumentsBypassFlags(LaunchResult result) {
        String out = result.getOutput();
        assertThat(out).contains("--yes");
        assertThat(out).contains("--allow-partial");
        assertThat(out).contains("--partial-ports");
        assertThat(out).contains("--dry-run");
        assertThat(out).contains("Examples:");
    }

    @Test
    @Launch({"docker", "--dry-run", "--allow-partial", "--", "run", "-d", "-e", "A=1", "--name", "x", "nginx:1"})
    void dockerDryRunPrintsTranslation(LaunchResult result) {
        String out = result.getOutput();
        assertThat(result.exitCode()).isZero();
        assertThat(out).contains("xion run");
        assertThat(out).contains("--name");
        assertThat(out).contains("x");
        assertThat(out).contains("(dry-run) not executed");
    }

    @Test
    @Launch({"docker", "--print-only", "--yes", "--", "ps"})
    void dockerPrintOnlyEmitsShellLine(LaunchResult result) {
        assertThat(result.exitCode()).isZero();
        assertThat(result.getOutput()).contains("xion ps");
    }
}
