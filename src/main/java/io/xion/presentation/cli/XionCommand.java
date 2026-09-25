package io.xion.presentation.cli;

import io.quarkus.picocli.runtime.annotations.TopCommand;
import picocli.CommandLine;

@TopCommand
@CommandLine.Command(
        name = "xion",
        mixinStandardHelpOptions = true,
        versionProvider = XionVersionProvider.class,
        sortOptions = false,
        showDefaultValues = true,
        usageHelpAutoWidth = true,
        synopsisHeading = "%nUsage:%n  ",
        descriptionHeading = "%nDescription:%n%n",
        parameterListHeading = "%nArguments:%n%n",
        optionListHeading = "%nOptions:%n%n",
        commandListHeading = "%nCommands:%n%n",
        footerHeading = "%nExamples:%n%n",
        header = {
                "Xion — macOS Apple Silicon container runtime",
                "(Seatbelt sandbox + loopback proxy + SQLite state)"
        },
        description = {
                "Xion isolates host processes with macOS Seatbelt (sandbox-exec), publishes",
                "ports through a daemon-side NIO proxy, applies memory/CPU limits, and stores",
                "container metadata in SQLite. The CLI talks to a background daemon over a",
                "Unix domain socket (~/.xion/xion.sock).",
                "",
                "Typical flow:",
                "  1. xion daemon install        # launchd KeepAlive (or: daemon start)",
                "  2. xion network create appnet # optional bridge",
                "  3. xion run …                 # create + start a sandboxed process",
                "  4. xion ps | ps -a | logs | stop | update | network ls",
                "  5. xion daemon doctor | stop  # health / shutdown",
                "",
                "Coming from Docker? Use:  xion docker --help",
                "                          xion docker --yes --allow-partial -- run …",
                "",
                "Run 'xion COMMAND --help' for detailed help on any subcommand.",
                "Run 'xion help COMMAND' as an alias for COMMAND --help."
        },
        footer = {
                "  xion daemon start",
                "  xion run --name web -p 8080:80 --memory 256m -- /usr/bin/python3 -m http.server 80",
                "  xion ps",
                "  xion ps -a",
                "  xion network ls",
                "  xion update web --memory 512m --network frontend",
                "  xion logs web",
                "  xion stop web",
                "  xion daemon stop",
                "  xion docker --yes --allow-partial -- run -d -p 8080:80 nginx",
                "",
                "Docs: README.md  |  Socket: ~/.xion/xion.sock  |  Profiles: /tmp/xion-profiles/",
                "Exit status: 0 success, non-zero on daemon/CLI errors (see each command)."
        },
        subcommands = {
                DaemonCommand.class,
                VersionCommand.class,
                RunCommand.class,
                CreateCommand.class,
                StartCommand.class,
                StopCommand.class,
                LogsCommand.class,
                PsCommand.class,
                InspectCommand.class,
                UpdateCommand.class,
                RmCommand.class,
                NetworkCommand.class,
                DockerCompatCommand.class,
                CommandLine.HelpCommand.class
        })
public class XionCommand implements Runnable {

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().usage(spec.commandLine().getOut());
    }
}
