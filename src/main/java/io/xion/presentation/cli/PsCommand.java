package io.xion.presentation.cli;

import jakarta.inject.Inject;
import picocli.CommandLine;

import java.util.concurrent.Callable;

@CommandLine.Command(name = "ps", description = "List containers")
public class PsCommand implements Callable<Integer> {

    @Inject
    DaemonClientSupport client;

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public Integer call() throws Exception {
        var response = client.send("ps", client.object());
        if (!response.ok()) {
            spec.commandLine().getErr().println(response.error());
            return 1;
        }
        var out = spec.commandLine().getOut();
        out.printf("%-14s %-20s %-10s %s%n", "ID", "NAME", "STATUS", "BINARY");
        response.payload().path("containers").forEach(c ->
                out.printf("%-14s %-20s %-10s %s%n",
                        c.path("id").asText(),
                        c.path("name").asText(),
                        c.path("status").asText(),
                        c.path("binary").asText()));
        return 0;
    }
}
