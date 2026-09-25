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
                "Manage in-daemon bridge networks with per-app IPs.",
                "",
                "A bridge owns a private subnet (default 10.89.N.0/24). Attached containers",
                "get a unique loopback IP so they can share the same listen port; the daemon",
                "reverse-proxies published host ports to containerIP:containerPort.",
                "Name resolution maps http://container-b to that endpoint.",
                "",
                "create    Create a bridge (optional --subnet CIDR)",
                "ls        List networks (subnet, gateway, members)",
                "inspect   Show members/IPs/endpoints as JSON",
                "rm        Remove a network"
        },
        footer = {
                "  xion network create frontend",
                "  xion network create backend --subnet 10.89.5.0/24",
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
