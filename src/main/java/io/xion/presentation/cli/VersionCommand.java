package io.xion.presentation.cli;

import picocli.CommandLine;

@CommandLine.Command(
        name = "version",
        mixinStandardHelpOptions = true,
        synopsisHeading = "%nUsage:%n  ",
        descriptionHeading = "%nDescription:%n%n",
        description = {
                "Print the Xion CLI / runtime version string.",
                "",
                "Does not require the daemon. Equivalent to `xion --version` / `-V`."
        })
public class VersionCommand implements Runnable {

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().getOut().println("xion " + XionVersionProvider.VERSION);
    }
}
