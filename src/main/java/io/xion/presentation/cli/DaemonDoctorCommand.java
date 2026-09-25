package io.xion.presentation.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.xion.infrastructure.ipc.UnixDomainSocketClient;
import io.xion.infrastructure.process.DaemonProcessSupport;
import io.xion.infrastructure.process.LaunchdDaemonInstaller;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "doctor",
        mixinStandardHelpOptions = true,
        synopsisHeading = "%nUsage:%n  ",
        descriptionHeading = "%nDescription:%n%n",
        description = {
                "Check daemon health: pidfile, socket, launchd, and stale sock cleanup."
        })
public class DaemonDoctorCommand implements Callable<Integer> {

    @Inject
    UnixDomainSocketClient socketClient;

    @Inject
    ObjectMapper mapper;

    @ConfigProperty(name = "xion.runtime-dir")
    String runtimeDir;

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public Integer call() throws Exception {
        Path rt = Path.of(runtimeDir);
        Path sock = socketClient.socketPath();
        List<String> problems = new ArrayList<>();
        var out = spec.commandLine().getOut();

        out.println("runtime-dir: " + rt.toAbsolutePath());
        out.println("socket:      " + sock.toAbsolutePath());
        out.println("pidfile:     " + DaemonProcessSupport.pidFile(rt).toAbsolutePath());
        out.println("log:         " + DaemonProcessSupport.logFile(rt).toAbsolutePath());

        Optional<Long> pid = DaemonProcessSupport.readPid(rt);
        if (pid.isPresent()) {
            boolean alive = DaemonProcessSupport.isAlive(pid.get());
            out.println("pid:         " + pid.get() + (alive ? " (alive)" : " (DEAD — stale pidfile)"));
            if (!alive) {
                problems.add("stale pidfile");
                DaemonProcessSupport.clearPid(rt);
            }
        } else {
            out.println("pid:         (none)");
        }

        boolean sockExists = Files.exists(sock);
        out.println("sock file:   " + (sockExists ? "present" : "absent"));
        if (sockExists && DaemonProcessSupport.livingDaemonPid(rt).isEmpty()) {
            boolean removed = DaemonProcessSupport.clearStaleSocket(sock, rt);
            if (removed) {
                out.println("cleaned:     removed stale socket (daemon pid was dead)");
                problems.add("had stale socket");
            }
        }

        boolean ping;
        try {
            ping = socketClient.send("ping", mapper.createObjectNode()).ok();
        } catch (Exception e) {
            ping = false;
        }
        out.println("ping:        " + (ping ? "ok" : "FAIL"));
        if (!ping) {
            problems.add("daemon not reachable");
        }

        if (LaunchdDaemonInstaller.isMac()) {
            out.println("launchd:     plist=" + LaunchdDaemonInstaller.isInstalled()
                    + " loaded=" + LaunchdDaemonInstaller.isLoaded()
                    + " (" + LaunchdDaemonInstaller.plistPath() + ")");
            if (!LaunchdDaemonInstaller.isInstalled()) {
                out.println("tip:         xion daemon install   # KeepAlive across reboot/kill");
            }
        } else {
            out.println("launchd:     n/a (not macOS)");
        }

        if (problems.isEmpty() && ping) {
            out.println("doctor:      OK");
            return 0;
        }
        out.println("doctor:      issues — " + String.join(", ", problems));
        return ping ? 0 : 1;
    }
}
