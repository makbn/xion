package io.xion.presentation.cli;

import jakarta.inject.Inject;
import picocli.CommandLine;

import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "stop",
        mixinStandardHelpOptions = true,
        synopsisHeading = "%nUsage:%n  ",
        descriptionHeading = "%nDescription:%n%n",
        parameterListHeading = "%nArguments:%n%n",
        footerHeading = "%nExamples:%n%n",
        description = {
                "Stop a running container by id or name.",
                "",
                "Sends SIGTERM (then SIGKILL if needed), tears down the NIO port proxy,",
                "detaches bridge membership, and marks status STOPPED in SQLite.",
                "",
                "Requires a running daemon."
        },
        footer = {
                "  xion stop web",
                "  xion stop 3fa2c1b9e0ab"
        })
public class StopCommand implements Callable<Integer> {

    @Inject
    DaemonClientSupport client;

    @CommandLine.Parameters(index = "0", paramLabel = "CONTAINER",
            description = "Container id or name.")
    String id;

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public Integer call() throws Exception {
        var response = client.send("stop", client.object().put("id", id));
        if (!response.ok()) {
            spec.commandLine().getErr().println(response.error());
            return 1;
        }
        spec.commandLine().getOut().println(response.payload().get("id").asText());
        return 0;
    }
}
