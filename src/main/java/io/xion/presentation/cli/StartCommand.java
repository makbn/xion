package io.xion.presentation.cli;

import jakarta.inject.Inject;
import picocli.CommandLine;

import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "start",
        mixinStandardHelpOptions = true,
        synopsisHeading = "%nUsage:%n  ",
        descriptionHeading = "%nDescription:%n%n",
        parameterListHeading = "%nArguments:%n%n",
        footerHeading = "%nExamples:%n%n",
        description = {
                "Start a previously created (or stopped) container by id or name.",
                "",
                "Generates/refreshes the Seatbelt profile, applies ResourceGovernor limits,",
                "starts port proxies for published ports, then spawns the process.",
                "",
                "Requires a running daemon. Fails if the container is already RUNNING."
        },
        footer = {
                "  xion create --name job -- /usr/bin/sleep 10",
                "  xion start job",
                "  xion start 3fa2c1b9e0ab"
        })
public class StartCommand implements Callable<Integer> {

    @Inject
    DaemonClientSupport client;

    @CommandLine.Parameters(index = "0", paramLabel = "CONTAINER",
            description = "Container id (12-char hex) or unique name.")
    String id;

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public Integer call() throws Exception {
        var response = client.send("start", client.object().put("id", id));
        if (!response.ok()) {
            spec.commandLine().getErr().println(response.error());
            return 1;
        }
        spec.commandLine().getOut().println(response.payload().get("id").asText());
        return 0;
    }
}
