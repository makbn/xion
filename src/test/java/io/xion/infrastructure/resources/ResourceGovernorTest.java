package io.xion.infrastructure.resources;

import io.xion.domain.ResourceLimits;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ResourceGovernorTest {

    @Test
    void fakeRecordsAppliedLimitsViaWrap() {
        FakeResourceGovernor gov = new FakeResourceGovernor();
        ResourceLimits limits = ResourceLimits.of("128m", 1.0);
        gov.apply(limits);
        assertThat(gov.lastApplied()).isSameAs(limits);
        assertThat(gov.wrapCommand(List.of("/bin/true"), limits)).containsExactly("/bin/true");
    }

    @Test
    @Tag("darwin")
    @EnabledOnOs(OS.MAC)
    void darwinWrapsWithTaskpolicyWhenCpusSet() {
        DarwinResourceGovernor gov = new DarwinResourceGovernor();
        ResourceLimits limits = ResourceLimits.of(null, 2.0);
        List<String> wrapped = gov.wrapCommand(List.of("/usr/bin/true"), limits);
        assertThat(wrapped.getFirst()).isEqualTo("/usr/sbin/taskpolicy");
        assertThat(wrapped).containsSubsequence("/usr/sbin/taskpolicy", "-c", "utility");
        assertThat(wrapped).endsWith("/usr/bin/true");
    }

    @Test
    @Tag("darwin")
    @EnabledOnOs(OS.MAC)
    void darwinWrapsMemoryWithChildScopedUlimitNotDaemonSetrlimit() {
        DarwinResourceGovernor gov = new DarwinResourceGovernor();
        ResourceLimits limits = ResourceLimits.of("64m", null);
        List<String> wrapped = gov.wrapCommand(List.of("/usr/bin/true"), limits);
        assertThat(wrapped.getFirst()).isEqualTo("/bin/sh");
        assertThat(wrapped.get(2)).contains("ulimit");
        assertThat(wrapped).endsWith("/usr/bin/true");
        // apply must remain a no-op on the daemon
        gov.apply(limits);
    }

    @Test
    @Tag("darwin")
    @EnabledOnOs(OS.MAC)
    void darwinMemoryThenCpusOrder() {
        DarwinResourceGovernor gov = new DarwinResourceGovernor();
        ResourceLimits limits = ResourceLimits.of("32m", 1.0);
        List<String> wrapped = gov.wrapCommand(List.of("/usr/bin/true"), limits);
        assertThat(wrapped.getFirst()).isEqualTo("/usr/sbin/taskpolicy");
        assertThat(wrapped).contains("/bin/sh");
    }
}
