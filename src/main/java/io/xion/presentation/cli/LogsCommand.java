package io.xion.presentation.cli;

import jakarta.inject.Inject;
import picocli.CommandLine;

import java.util.concurrent.Callable;

@CommandLine.Command(name = "logs", description = "Show container logs")
public class LogsCommand implements Callable<Integer> {

    @Inject
    DaemonClientSupport client;

    @CommandLine.Option(names = "--stderr", description = "Show stderr instead of stdout")
    boolean stderr;

    @CommandLine.Parameters(index = "0")
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
