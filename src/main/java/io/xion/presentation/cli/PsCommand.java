package io.xion.presentation.cli;

import jakarta.inject.Inject;
import picocli.CommandLine;

import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "ps",
        mixinStandardHelpOptions = true,
        synopsisHeading = "%nUsage:%n  ",
        descriptionHeading = "%nDescription:%n%n",
        optionListHeading = "%nOptions:%n%n",
        footerHeading = "%nExamples:%n%n",
        description = {
                "List containers (Docker-compatible defaults).",
                "",
                "Without flags, shows only RUNNING containers.",
                "With -a/--all, shows every status: CREATED, RUNNING, STOPPED, EXITED.",
                "",
                "Columns: ID, NAME, STATUS, NETWORK, BINARY.",
                "",
                "Requires a running daemon."
        },
        footer = {
                "  xion ps              # running only",
                "  xion ps -a           # all statuses",
                "  xion ps -aq          # all ids, quiet",
                "  xion docker --yes -- ps -a"
        })
public class PsCommand implements Callable<Integer> {

    @Inject
    DaemonClientSupport client;

    @CommandLine.Option(
            names = {"-a", "--all"},
            description = "Show all containers (default shows only RUNNING).")
    boolean all;

    @CommandLine.Option(
            names = {"-q", "--quiet"},
            description = "Display only container IDs.")
    boolean quiet;

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public Integer call() throws Exception {
        var response = client.send("ps", client.object().put("all", all));
        if (!response.ok()) {
            spec.commandLine().getErr().println(response.error());
            return 1;
        }
        var out = spec.commandLine().getOut();
        var containers = response.payload().path("containers");
        if (quiet) {
            containers.forEach(c -> out.println(c.path("id").asText()));
            return 0;
        }
        out.printf("%-14s %-20s %-10s %-14s %s%n", "ID", "NAME", "STATUS", "NETWORK", "BINARY");
        containers.forEach(c ->
                out.printf("%-14s %-20s %-10s %-14s %s%n",
                        c.path("id").asText(),
                        c.path("name").asText(),
                        c.path("status").asText(),
                        c.path("network").asText("-"),
                        c.path("binary").asText()));
        return 0;
    }
}
