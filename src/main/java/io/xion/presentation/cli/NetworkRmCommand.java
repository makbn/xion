package io.xion.presentation.cli;

import jakarta.inject.Inject;
import picocli.CommandLine;

import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "rm",
        aliases = {"remove"},
        mixinStandardHelpOptions = true,
        synopsisHeading = "%nUsage:%n  ",
        descriptionHeading = "%nDescription:%n%n",
        optionListHeading = "%nOptions:%n%n",
        parameterListHeading = "%nArguments:%n%n",
        footerHeading = "%nExamples:%n%n",
        description = {
                "Remove a bridge network.",
                "",
                "Refuses if containers are still attached unless --force",
                "(force detaches members then deletes the network).",
                "",
                "Requires a running daemon."
        },
        footer = {
                "  xion network rm frontend",
                "  xion network rm --force frontend"
        })
public class NetworkRmCommand implements Callable<Integer> {

    @Inject
    DaemonClientSupport client;

    @CommandLine.Option(names = {"-f", "--force"},
            description = "Detach any members, then remove the network.")
    boolean force;

    @CommandLine.Parameters(index = "0", paramLabel = "NAME")
    String name;

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public Integer call() throws Exception {
        var response = client.send("network.rm",
                client.object().put("name", name).put("force", force));
        if (!response.ok()) {
            spec.commandLine().getErr().println(response.error());
            return 1;
        }
        spec.commandLine().getOut().println(response.payload().get("name").asText());
        return 0;
    }
}
