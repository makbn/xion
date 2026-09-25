package io.xion.presentation.cli;

import jakarta.inject.Inject;
import picocli.CommandLine;

import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "ps",
        mixinStandardHelpOptions = true,
        synopsisHeading = "%nUsage:%n  ",
        descriptionHeading = "%nDescription:%n%n",
        footerHeading = "%nExamples:%n%n",
        description = {
                "List all containers known to the daemon (any status).",
                "",
                "Columns: ID, NAME, STATUS, BINARY. Status values: CREATED, RUNNING,",
                "STOPPED, EXITED. Data comes from SQLite via the daemon UDS API.",
                "",
                "Docker filters (-a, -q, --filter) are not supported; translate with",
                "`xion docker --allow-partial -- ps -a` to drop them.",
                "",
                "Requires a running daemon."
        },
        footer = {
                "  xion ps",
                "  xion docker --yes --allow-partial -- ps -a"
        })
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
