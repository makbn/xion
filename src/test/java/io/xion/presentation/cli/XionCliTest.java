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
    void helpListsSubcommands(LaunchResult result) {
        String out = result.getOutput();
        assertThat(out).contains("daemon");
        assertThat(out).contains("run");
        assertThat(out).contains("ps");
        assertThat(out).contains("version");
    }
}
