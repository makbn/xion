package io.xion.presentation.cli;

import picocli.CommandLine;

@CommandLine.Command(
        name = "daemon",
        description = "Manage the Xion daemon",
        subcommands = {DaemonStartCommand.class, DaemonStopCommand.class})
public class DaemonCommand implements Runnable {

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().usage(spec.commandLine().getOut());
    }
}
