package io.xion.presentation.cli;

import io.xion.application.mediator.DaemonService;
import jakarta.inject.Inject;
import picocli.CommandLine;

@CommandLine.Command(name = "start", description = "Start the Xion daemon (UDS server)")
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
