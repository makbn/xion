package io.xion.presentation.cli;

import picocli.CommandLine;

@CommandLine.Command(name = "version", description = "Print Xion version")
public class VersionCommand implements Runnable {

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().getOut().println("xion " + XionVersionProvider.VERSION);
    }
}
