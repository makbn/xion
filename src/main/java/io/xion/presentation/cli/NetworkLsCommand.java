package io.xion.presentation.cli;

import jakarta.inject.Inject;
import picocli.CommandLine;

import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "ls",
        aliases = {"list"},
        mixinStandardHelpOptions = true,
        synopsisHeading = "%nUsage:%n  ",
        descriptionHeading = "%nDescription:%n%n",
        optionListHeading = "%nOptions:%n%n",
        footerHeading = "%nExamples:%n%n",
        description = {
                "List bridge networks known to the daemon.",
                "",
                "Columns: NAME, MEMBERS (count), MEMBER NAMES.",
                "",
                "Requires a running daemon."
        },
        footer = {
                "  xion network ls",
                "  xion network create frontend && xion network ls"
        })
public class NetworkLsCommand implements Callable<Integer> {

    @Inject
    DaemonClientSupport client;

    @CommandLine.Option(names = {"-q", "--quiet"}, description = "Display only network names.")
    boolean quiet;

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public Integer call() throws Exception {
        var response = client.send("network.ls", client.object());
        if (!response.ok()) {
            spec.commandLine().getErr().println(response.error());
            return 1;
        }
        var out = spec.commandLine().getOut();
        var networks = response.payload().path("networks");
        if (quiet) {
            networks.forEach(n -> out.println(n.path("name").asText()));
            return 0;
        }
        out.printf("%-20s %-8s %s%n", "NAME", "MEMBERS", "CONTAINERS");
        networks.forEach(n -> {
            String members = "";
            if (n.path("members").isArray()) {
                StringBuilder sb = new StringBuilder();
                n.path("members").forEach(m -> {
                    if (!sb.isEmpty()) {
                        sb.append(',');
                    }
                    sb.append(m.asText());
                });
                members = sb.toString();
            }
            out.printf("%-20s %-8d %s%n",
                    n.path("name").asText(),
                    n.path("memberCount").asInt(0),
                    members.isBlank() ? "-" : members);
        });
        return 0;
    }
}
