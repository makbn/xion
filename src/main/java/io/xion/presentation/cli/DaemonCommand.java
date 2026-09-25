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
                "start      Detach into the background by default (pidfile ~/.xion/xion.pid).",
                "stop       SIGTERM the daemon via the pidfile.",
                "install    launchd KeepAlive agent (macOS) — survives reboot/kill -9.",
                "uninstall  Remove the launchd agent.",
                "doctor     Pidfile / socket / launchd / stale-sock checks."
        },
        footer = {
                "  xion daemon install   # always-on hosts",
                "  xion daemon start     # or one-shot background without launchd",
                "  xion ps",
                "  xion daemon doctor",
                "  xion daemon stop"
        },
        subcommands = {
                DaemonStartCommand.class,
                DaemonStopCommand.class,
                DaemonInstallCommand.class,
                DaemonUninstallCommand.class,
                DaemonDoctorCommand.class
        })
public class DaemonCommand implements Runnable {

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().usage(spec.commandLine().getOut());
    }
}
