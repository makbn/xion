package io.xion.presentation.cli;

import picocli.CommandLine;

@CommandLine.Command(
        name = "network",
        mixinStandardHelpOptions = true,
        synopsisHeading = "%nUsage:%n  ",
        descriptionHeading = "%nDescription:%n%n",
        commandListHeading = "%nCommands:%n%n",
        footerHeading = "%nExamples:%n%n",
        description = {
                "Manage in-daemon bridge networks.",
                "",
                "A bridge is a named registry of container members. Attached containers can",
                "resolve each other's names (http://container-b) to loopback endpoints.",
                "This is not a full Docker CNI stack — no iptables, no overlay.",
                "",
                "create    Create an empty bridge by name",
                "ls        List networks",
                "inspect   Show members/endpoints as JSON",
                "rm        Remove a network"
        },
        footer = {
                "  xion network create frontend",
                "  xion network ls",
                "  xion network inspect frontend",
                "  xion update web --network frontend",
                "  xion network rm frontend"
        },
        subcommands = {
                NetworkCreateCommand.class,
                NetworkLsCommand.class,
                NetworkInspectCommand.class,
                NetworkRmCommand.class
        })
public class NetworkCommand implements Runnable {

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().usage(spec.commandLine().getOut());
    }
}
