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
                "Show JSON details for a bridge network (members + endpoints).",
                "",
                "Requires a running daemon."
        },
        footer = {
                "  xion network inspect frontend"
        })
public class NetworkInspectCommand implements Callable<Integer> {

    @Inject
    DaemonClientSupport client;

    @Inject
    ObjectMapper mapper;

    @CommandLine.Parameters(index = "0", paramLabel = "NAME")
    String name;

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public Integer call() throws Exception {
        var response = client.send("network.inspect", client.object().put("name", name));
        if (!response.ok()) {
            spec.commandLine().getErr().println(response.error());
            return 1;
        }
        spec.commandLine().getOut().println(
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(response.payload()));
        return 0;
    }
}
