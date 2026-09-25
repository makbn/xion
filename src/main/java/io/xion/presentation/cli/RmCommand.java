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
                "Remove a container from the SQLite store (and detach its network).",
                "",
                "Refuses to remove a RUNNING container unless --force (stop+remove).",
                "",
                "Requires a running daemon."
        },
        footer = {
                "  xion stop web && xion rm web",
                "  xion rm --force web"
        })
public class RmCommand implements Callable<Integer> {

    @Inject
    DaemonClientSupport client;

    @CommandLine.Option(names = {"-f", "--force"},
            description = "Stop a running container, then remove it.")
    boolean force;

    @CommandLine.Parameters(index = "0", paramLabel = "CONTAINER")
    String id;

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public Integer call() throws Exception {
        var response = client.send("rm", client.object().put("id", id).put("force", force));
        if (!response.ok()) {
            spec.commandLine().getErr().println(response.error());
            return 1;
        }
        spec.commandLine().getOut().println(response.payload().get("id").asText());
        return 0;
    }
}
