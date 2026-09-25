package io.xion.presentation.cli;

import jakarta.inject.Inject;
import picocli.CommandLine;

@CommandLine.Command(
        name = "stop",
        mixinStandardHelpOptions = true,
        synopsisHeading = "%nUsage:%n  ",
        descriptionHeading = "%nDescription:%n%n",
        description = {
                "Check that the daemon answers a ping on the UDS, then instruct you to",
                "send SIGTERM to the daemon process (graceful shutdown hook calls stop).",
                "",
                "This does not kill containers by itself; stop containers first with",
                "`xion stop` if needed."
        })
public class DaemonStopCommand implements Runnable {

    @Inject
    DaemonClientSupport client;

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public void run() {
        try {
            var response = client.send("ping", client.mapper().createObjectNode());
            if (!response.ok()) {
                throw new IllegalStateException(response.error());
            }
            spec.commandLine().getOut().println("Daemon is reachable at " + client.socketPath()
                    + ". Send SIGTERM to the daemon process to stop.");
        } catch (Exception e) {
            throw new CommandLine.ExecutionException(
                    new CommandLine(this), "Daemon not reachable: " + e.getMessage(), e);
        }
    }
}
