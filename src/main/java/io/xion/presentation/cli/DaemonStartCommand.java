package io.xion.presentation.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.xion.application.mediator.DaemonService;
import io.xion.infrastructure.ipc.UnixDomainSocketClient;
import io.xion.infrastructure.process.DaemonProcessSupport;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "start",
        mixinStandardHelpOptions = true,
        showDefaultValues = true,
        synopsisHeading = "%nUsage:%n  ",
        descriptionHeading = "%nDescription:%n%n",
        optionListHeading = "%nOptions:%n%n",
        footerHeading = "%nNotes:%n%n",
        description = {
                "Start the Xion daemon.",
                "",
                "By default the daemon detaches into the background (pidfile + log under",
                "~/.xion), like dockerd/podman — the CLI exits once the socket is ready.",
                "Use --foreground to keep it attached to the terminal for debugging."
        },
        footer = {
                "  xion daemon start",
                "  xion daemon start --foreground",
                "  xion daemon stop"
        })
public class DaemonStartCommand implements Callable<Integer> {

    @Inject
    DaemonService daemonService;

    @Inject
    UnixDomainSocketClient socketClient;

    @Inject
    ObjectMapper mapper;

    @ConfigProperty(name = "xion.runtime-dir")
    String runtimeDir;

    @CommandLine.Option(
            names = {"--foreground", "-f"},
            description = "Run in the foreground and block until SIGTERM/SIGINT (do not detach).")
    boolean foreground;

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public Integer call() throws Exception {
        Path rt = Path.of(runtimeDir);
        Files.createDirectories(rt);
        // Dead pid + leftover sock → CF 502 / "daemon up?" confusion
        DaemonProcessSupport.clearStaleSocket(socketClient.socketPath(), rt);

        Optional<Long> existing = DaemonProcessSupport.livingDaemonPid(rt);
        if (existing.isPresent() && pingOk()) {
            spec.commandLine().getOut().println(
                    "Daemon already running (pid " + existing.get() + ") at " + socketClient.socketPath());
            return 0;
        }

        if (!foreground) {
            return startDetached(rt);
        }
        return startForeground(rt);
    }

    private Integer startDetached(Path rt) throws Exception {
        if (pingOk()) {
            spec.commandLine().getOut().println(
                    "Daemon already reachable at " + socketClient.socketPath() + " (no pidfile); not starting another.");
            return 0;
        }
        long pid = DaemonProcessSupport.spawnDetached(rt);
        boolean ready = DaemonProcessSupport.waitUntil(this::pingOk, 15_000);
        if (!ready) {
            spec.commandLine().getErr().println(
                    "Daemon process started (pid " + pid + ") but socket not ready. Check "
                            + DaemonProcessSupport.logFile(rt));
            return 1;
        }
        spec.commandLine().getOut().println(
                "Daemon started in background (pid " + pid + ", log "
                        + DaemonProcessSupport.logFile(rt) + ")");
        return 0;
    }

    private Integer startForeground(Path rt) throws Exception {
        long pid = ProcessHandle.current().pid();
        DaemonProcessSupport.writePid(rt, pid);
        try {
            daemonService.startAndBlock();
            return 0;
        } catch (Exception e) {
            throw new CommandLine.ExecutionException(
                    spec.commandLine(), "Failed to start daemon: " + e.getMessage(), e);
        } finally {
            DaemonProcessSupport.clearPid(rt);
        }
    }

    private boolean pingOk() {
        try {
            var response = socketClient.send("ping", mapper.createObjectNode());
            return response.ok();
        } catch (Exception e) {
            return false;
        }
    }
}
