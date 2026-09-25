package io.xion.presentation.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import picocli.CommandLine;

import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "inspect",
        mixinStandardHelpOptions = true,
        synopsisHeading = "%nUsage:%n  ",
        descriptionHeading = "%nDescription:%n%n",
        parameterListHeading = "%nArguments:%n%n",
        footerHeading = "%nExamples:%n%n",
        description = {
                "Show low-level information on a container as JSON",
                "(status, network, profile limits/ports/volumes, timestamps).",
                "",
                "Requires a running daemon."
        },
        footer = {
                "  xion inspect web",
                "  xion inspect 3fa2c1b9e0ab"
        })
public class InspectCommand implements Callable<Integer> {

    @Inject
    DaemonClientSupport client;

    @Inject
    ObjectMapper mapper;

    @CommandLine.Parameters(index = "0", paramLabel = "CONTAINER")
    String id;

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public Integer call() throws Exception {
        var response = client.send("inspect", client.object().put("id", id));
        if (!response.ok()) {
            spec.commandLine().getErr().println(response.error());
            return 1;
        }
        spec.commandLine().getOut().println(
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(response.payload()));
        return 0;
    }
}
