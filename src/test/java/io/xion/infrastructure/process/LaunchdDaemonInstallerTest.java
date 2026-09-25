package io.xion.infrastructure.process;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LaunchdDaemonInstallerTest {

    @TempDir
    Path temp;

    @Test
    void renderPlistContainsKeepAliveAndForeground() {
        List<String> args = List.of("/usr/local/bin/xion", "daemon", "start", "--foreground");
        String plist = LaunchdDaemonInstaller.renderPlist(
                args, temp, temp.resolve("daemon.log"));
        assertThat(plist).contains("<string>io.xion.daemon</string>");
        assertThat(plist).contains("<key>KeepAlive</key>");
        assertThat(plist).contains("<true/>");
        assertThat(plist).contains("<string>/usr/local/bin/xion</string>");
        assertThat(plist).contains("<string>--foreground</string>");
        assertThat(plist).contains("daemon.log");
    }

    @Test
    void clearStaleSocketRemovesWhenPidDead() throws Exception {
        Path sock = temp.resolve("xion.sock");
        Files.writeString(sock, "");
        DaemonProcessSupport.writePid(temp, 999_999_999L); // almost certainly dead
        assertThat(DaemonProcessSupport.clearStaleSocket(sock, temp)).isTrue();
        assertThat(Files.exists(sock)).isFalse();
    }

    @Test
    void clearStaleSocketKeepsWhenPidAlive() throws Exception {
        Path sock = temp.resolve("xion.sock");
        Files.writeString(sock, "");
        DaemonProcessSupport.writePid(temp, ProcessHandle.current().pid());
        assertThat(DaemonProcessSupport.clearStaleSocket(sock, temp)).isFalse();
        assertThat(Files.exists(sock)).isTrue();
    }
}
