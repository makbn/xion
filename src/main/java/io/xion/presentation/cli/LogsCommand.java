package io.xion.presentation.cli;

import jakarta.inject.Inject;
import picocli.CommandLine;

import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "logs",
        mixinStandardHelpOptions = true,
        synopsisHeading = "%nUsage:%n  ",
        descriptionHeading = "%nDescription:%n%n",
        optionListHeading = "%nOptions:%n%n",
        parameterListHeading = "%nArguments:%n%n",
        footerHeading = "%nExamples:%n%n",
        description = {
                "Print captured stdout (default) or stderr for a container.",
                "",
                "Logs are files under the container runtime dir:",
                "  ~/.xion/containers/<id>/stdout.log",
                "  ~/.xion/containers/<id>/stderr.log",
                "",
                "Follow/tail are not implemented yet (use `xion docker logs` with",
                "--allow-partial to drop -f/--tail when translating from Docker).",
                "",
                "Requires a running daemon."
        },
        footer = {
                "  xion logs web",
                "  xion logs --stderr web"
        })
public class LogsCommand implements Callable<Integer> {

    @Inject
    DaemonClientSupport client;

    @CommandLine.Option(names = "--stderr",
            description = "Show stderr.log instead of stdout.log.")
    boolean stderr;

    @CommandLine.Parameters(index = "0", paramLabel = "CONTAINER",
            description = "Container id or name.")
    String id;

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public Integer call() throws Exception {
        var response = client.send("logs", client.object().put("id", id).put("stderr", stderr));
        if (!response.ok()) {
            spec.commandLine().getErr().println(response.error());
            return 1;
        }
        spec.commandLine().getOut().print(response.payload().path("content").asText(""));
        return 0;
    }
}
