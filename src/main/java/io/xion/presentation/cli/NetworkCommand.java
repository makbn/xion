package io.xion.presentation.cli;

import picocli.CommandLine;

@CommandLine.Command(
        name = "network",
        description = "Manage bridge networks",
        subcommands = {NetworkCreateCommand.class})
public class NetworkCommand implements Runnable {

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().usage(spec.commandLine().getOut());
    }
}
