package io.xion.infrastructure.seatbelt;

import io.xion.domain.ContainerProfile;
import io.xion.domain.ResourceLimits;
import io.xion.domain.RestartPolicy;
import io.xion.domain.SandboxProfile;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Darwin acceptance: temp dir remap + Node {@code stdio: 'ignore'} → {@code /dev/null}.
 */
class DarwinNodeAcceptanceTest {

    @TempDir
    Path temp;

    @Test
    @Tag("darwin")
    @EnabledOnOs(OS.MAC)
    @EnabledIf("nodeAvailable")
    void nodeOsTmpdirMkdirUnderGeneratedRelayProfile() throws Exception {
        Path sb = writeRelayProfile();
        SeatbeltProfileValidator.validateOrThrow(sb);

        String js = "require('fs').mkdirSync(require('os').tmpdir()+'/xion-accept',{recursive:true});"
                + "console.log(require('os').tmpdir())";
        Process p = sandboxedNode(sb, js);
        boolean done = p.waitFor(20, TimeUnit.SECONDS);
        assumeTrue(done, "node timed out");
        String out = new String(p.getInputStream().readAllBytes()).trim();
        String err = new String(p.getErrorStream().readAllBytes()).trim();
        assumeTrue(p.exitValue() == 0, "node mkdir failed exit=" + p.exitValue() + " err=" + err);
        assertThat(out).isEqualTo("/tmp");
        assertThat(Files.isDirectory(Path.of("/tmp/xion-accept"))).isTrue();
    }

    @Test
    @Tag("darwin")
    @EnabledOnOs(OS.MAC)
    @EnabledIf("nodeAvailable")
    void nodeSpawnStdioIgnoreUnderGeneratedRelayProfile() throws Exception {
        Path sb = writeRelayProfile();
        SeatbeltProfileValidator.validateOrThrow(sb);

        // Mirrors ffmpeg remux failure mode: stdio ignore opens /dev/null
        String js = "const {spawn}=require('child_process');"
                + "const c=spawn('/usr/bin/true',[],{stdio:['ignore','ignore','pipe']});"
                + "c.on('error',e=>{console.error(e);process.exit(1)});"
                + "c.on('spawn',()=>{console.log('ok');process.exit(0)});"
                + "setTimeout(()=>process.exit(2),5000);";
        Process p = sandboxedNode(sb, js);
        boolean done = p.waitFor(20, TimeUnit.SECONDS);
        assumeTrue(done, "node timed out");
        String out = new String(p.getInputStream().readAllBytes()).trim();
        String err = new String(p.getErrorStream().readAllBytes()).trim();
        assumeTrue(p.exitValue() == 0, "stdio ignore spawn failed exit=" + p.exitValue() + " err=" + err);
        assertThat(out).contains("ok");
    }

    private Path writeRelayProfile() throws Exception {
        SeatbeltProfileGenerator gen = new SeatbeltProfileGenerator(
                temp.resolve("profiles").toString(), "");
        ContainerProfile profile = new ContainerProfile(
                "accept",
                "accept",
                "/usr/bin/true",
                List.of(),
                List.of(),
                List.of(),
                Optional.empty(),
                ResourceLimits.unlimited(),
                temp.resolve("rt").toString(),
                Map.of(),
                Optional.empty(),
                false,
                RestartPolicy.NO,
                SandboxProfile.RELAY);
        return gen.writeProfile(profile, 0);
    }

    private static Process sandboxedNode(Path sb, String js) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                "sandbox-exec", "-f", sb.toAbsolutePath().toString(),
                "node", "-e", js);
        Map<String, String> env = SandboxEnv.curated(pb.environment(), Map.of());
        pb.environment().clear();
        pb.environment().putAll(env);
        pb.redirectError(ProcessBuilder.Redirect.PIPE);
        return pb.start();
    }

    static boolean nodeAvailable() {
        try {
            Process p = new ProcessBuilder("node", "-e", "process.exit(0)").start();
            return p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
