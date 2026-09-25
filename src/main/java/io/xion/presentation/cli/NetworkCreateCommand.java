package io.xion.presentation.cli;

import jakarta.inject.Inject;
import picocli.CommandLine;

import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "create",
        mixinStandardHelpOptions = true,
        synopsisHeading = "%nUsage:%n  ",
        descriptionHeading = "%nDescription:%n%n",
        parameterListHeading = "%nArguments:%n%n",
        footerHeading = "%nExamples:%n%n",
        description = {
                "Create a named bridge network in the daemon registry.",
                "",
                "Idempotent if the name already exists. Drivers, IPAM, and labels from",
                "`docker network create` are not supported — drop them via",
                "`xion docker --allow-partial -- network create …`."
        },
        footer = {
                "  xion network create frontend"
        })
public class NetworkCreateCommand implements Callable<Integer> {

    @Inject
    DaemonClientSupport client;

    @CommandLine.Parameters(index = "0", paramLabel = "NAME",
            description = "Bridge network name.")
    String name;

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public Integer call() throws Exception {
        var response = client.send("network.create", client.object().put("name", name));
        if (!response.ok()) {
            spec.commandLine().getErr().println(response.error());
            return 1;
        }
        spec.commandLine().getOut().println(response.payload().get("name").asText());
        return 0;
    }
}
