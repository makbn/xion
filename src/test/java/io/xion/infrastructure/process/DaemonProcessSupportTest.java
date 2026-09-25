package io.xion.infrastructure.process;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DaemonProcessSupportTest {

    @TempDir
    Path temp;

    @Test
    void pidFileRoundTripAndStaleClear() throws Exception {
        DaemonProcessSupport.writePid(temp, 12345L);
        assertThat(DaemonProcessSupport.readPid(temp)).contains(12345L);
        // unlikely pid — treat as dead and clear
        assertThat(DaemonProcessSupport.livingDaemonPid(temp)).isEmpty();
        assertThat(Files.exists(DaemonProcessSupport.pidFile(temp))).isFalse();
    }

    @Test
    void livingPidDetectsCurrentProcess() throws Exception {
        long self = ProcessHandle.current().pid();
        DaemonProcessSupport.writePid(temp, self);
        assertThat(DaemonProcessSupport.livingDaemonPid(temp)).contains(self);
        DaemonProcessSupport.clearPid(temp);
        assertThat(DaemonProcessSupport.readPid(temp)).isEmpty();
    }

    @Test
    void foregroundRelaunchEndsWithDaemonStartForeground() {
        List<String> cmd = DaemonProcessSupport.foregroundRelaunchCommand();
        assertThat(cmd).isNotEmpty();
        assertThat(cmd.getLast()).isEqualTo("--foreground");
        assertThat(cmd.get(cmd.size() - 2)).isEqualTo("start");
        assertThat(cmd.get(cmd.size() - 3)).isEqualTo("daemon");
    }

    @Test
    void wrapDetachedPreservesChildCommand() {
        List<String> child = List.of("/bin/echo", "hi");
        List<String> wrapped = DaemonProcessSupport.wrapDetached(child);
        assertThat(wrapped).endsWith("/bin/echo", "hi");
        assertThat(wrapped.size()).isGreaterThanOrEqualTo(child.size());
    }
}
