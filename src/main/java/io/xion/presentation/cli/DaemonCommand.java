package io.xion.presentation.cli;

import picocli.CommandLine;

@CommandLine.Command(
        name = "daemon",
        mixinStandardHelpOptions = true,
        synopsisHeading = "%nUsage:%n  ",
        descriptionHeading = "%nDescription:%n%n",
        commandListHeading = "%nCommands:%n%n",
        footerHeading = "%nExamples:%n%n",
        description = {
                "Manage the long-running Xion daemon process.",
                "",
                "The daemon owns the Unix domain socket (~/.xion/xion.sock), Mediator",
                "handlers, Seatbelt profile generation, port proxies, and SQLite store.",
                "All other CLI commands are thin IPC clients and require the daemon.",
                "",
                "start  Bind the UDS and block in the foreground (run under a service manager",
                "       or a dedicated terminal). Stop with SIGTERM.",
                "stop   Probe daemon reachability and print how to signal it."
        },
        footer = {
                "  xion daemon start",
                "  # elsewhere:",
                "  xion ps",
                "  kill -TERM $(pgrep -f 'xion.*daemon')"
        },
        subcommands = {DaemonStartCommand.class, DaemonStopCommand.class})
public class DaemonCommand implements Runnable {

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().usage(spec.commandLine().getOut());
    }
}
