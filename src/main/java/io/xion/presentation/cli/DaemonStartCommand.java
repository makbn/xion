package io.xion.presentation.cli;

import io.xion.application.mediator.DaemonService;
import jakarta.inject.Inject;
import picocli.CommandLine;

@CommandLine.Command(
        name = "start",
        mixinStandardHelpOptions = true,
        synopsisHeading = "%nUsage:%n  ",
        descriptionHeading = "%nDescription:%n%n",
        footerHeading = "%nNotes:%n%n",
        description = {
                "Start the Xion daemon and block until SIGTERM/SIGINT.",
                "",
                "Creates ~/.xion if needed, binds ~/.xion/xion.sock, and serves",
                "length-prefixed JSON IPC requests for create/start/stop/logs/ps/network."
        },
        footer = {
                "  xion daemon start",
                "  # or JVM: java -jar target/quarkus-app/quarkus-run.jar daemon start"
        })
public class DaemonStartCommand implements Runnable {

    @Inject
    DaemonService daemonService;

    @Override
    public void run() {
        try {
            daemonService.startAndBlock();
        } catch (Exception e) {
            throw new CommandLine.ExecutionException(
                    new CommandLine(this), "Failed to start daemon: " + e.getMessage(), e);
        }
    }
}
