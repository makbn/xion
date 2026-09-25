package io.xion.presentation.cli;

import io.quarkus.picocli.runtime.annotations.TopCommand;
import picocli.CommandLine;

@TopCommand
@CommandLine.Command(
        name = "xion",
        mixinStandardHelpOptions = true,
        versionProvider = XionVersionProvider.class,
        description = "Xion macOS Apple Silicon container runtime",
        subcommands = {
                DaemonCommand.class,
                VersionCommand.class,
                RunCommand.class,
                CreateCommand.class,
                StartCommand.class,
                StopCommand.class,
                LogsCommand.class,
                PsCommand.class,
                NetworkCommand.class
        })
public class XionCommand implements Runnable {

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().usage(spec.commandLine().getOut());
    }
}
