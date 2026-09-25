package io.xion.presentation.cli;

import jakarta.inject.Inject;
import picocli.CommandLine;

@CommandLine.Command(name = "stop", description = "Check daemon reachability / instruct stop")
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
