package io.xion.presentation.cli;

import jakarta.inject.Inject;
import picocli.CommandLine;

import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "create",
        mixinStandardHelpOptions = true,
        synopsisHeading = "%nUsage:%n  ",
        descriptionHeading = "%nDescription:%n%n",
        optionListHeading = "%nOptions:%n%n",
        parameterListHeading = "%nArguments:%n%n",
        footerHeading = "%nExamples:%n%n",
        description = {
                "Create a named bridge network with a private subnet (default 10.89.N.0/24).",
                "",
                "Each container attached to the network receives a unique IP (loopback alias)",
                "so two apps can both listen on the same container port (e.g. 8087) while",
                "publishing different host ports via the reverse proxy.",
                "",
                "Idempotent if the name already exists with the same subnet."
        },
        footer = {
                "  xion network create frontend",
                "  xion network create backend --subnet 10.89.5.0/24"
        })
public class NetworkCreateCommand implements Callable<Integer> {

    @Inject
    DaemonClientSupport client;

    @CommandLine.Parameters(index = "0", paramLabel = "NAME",
            description = "Bridge network name.")
    String name;

    @CommandLine.Option(names = {"--subnet"}, paramLabel = "CIDR",
            description = "IPv4 subnet for this network (default: next free 10.89.N.0/24).")
    String subnet;

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public Integer call() throws Exception {
        var payload = client.object().put("name", name);
        if (subnet != null && !subnet.isBlank()) {
            payload.put("subnet", subnet);
        }
        var response = client.send("network.create", payload);
        if (!response.ok()) {
            spec.commandLine().getErr().println(response.error());
            return 1;
        }
        var out = spec.commandLine().getOut();
        out.println(response.payload().get("name").asText());
        if (response.payload().hasNonNull("subnet")) {
            out.println("subnet=" + response.payload().get("subnet").asText()
                    + " gateway=" + response.payload().path("gateway").asText(""));
        }
        return 0;
    }
}
