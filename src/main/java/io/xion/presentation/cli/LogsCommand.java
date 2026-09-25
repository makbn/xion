package io.xion.presentation.cli;

import com.fasterxml.jackson.databind.node.ObjectNode;
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
                "Use --tail N for the last N lines. -f/--follow polls the daemon",
                "for new content (client-side follow of the growing log file).",
                "",
                "Requires a running daemon."
        },
        footer = {
                "  xion logs web",
                "  xion logs --stderr web",
                "  xion logs --tail 100 -f web"
        })
public class LogsCommand implements Callable<Integer> {

    @Inject
    DaemonClientSupport client;

    @CommandLine.Option(names = "--stderr",
            description = "Show stderr.log instead of stdout.log.")
    boolean stderr;

    @CommandLine.Option(names = {"-f", "--follow"},
            description = "Follow log output (poll until interrupted).")
    boolean follow;

    @CommandLine.Option(names = "--tail", paramLabel = "N",
            description = "Number of lines to show from the end of the logs (0 = all). Default: ${DEFAULT-VALUE}.",
            defaultValue = "0")
    int tail;

    @CommandLine.Parameters(index = "0", paramLabel = "CONTAINER",
            description = "Container id or name.")
    String id;

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public Integer call() throws Exception {
        if (!follow) {
            var response = sendLogs(tail);
            if (!response.ok()) {
                spec.commandLine().getErr().println(response.error());
                return 1;
            }
            spec.commandLine().getOut().print(response.payload().path("content").asText(""));
            return 0;
        }

        // Client-side follow: snapshot with optional --tail, then re-read full file for deltas.
        var first = sendLogs(tail);
        if (!first.ok()) {
            spec.commandLine().getErr().println(first.error());
            return 1;
        }
        String content = first.payload().path("content").asText("");
        spec.commandLine().getOut().print(content);
        spec.commandLine().getOut().flush();

        var full = sendLogs(0);
        if (!full.ok()) {
            spec.commandLine().getErr().println(full.error());
            return 1;
        }
        int printed = full.payload().path("content").asText("").length();

        while (!Thread.currentThread().isInterrupted()) {
            Thread.sleep(200);
            var next = sendLogs(0);
            if (!next.ok()) {
                spec.commandLine().getErr().println(next.error());
                return 1;
            }
            String all = next.payload().path("content").asText("");
            if (all.length() > printed) {
                spec.commandLine().getOut().print(all.substring(printed));
                spec.commandLine().getOut().flush();
                printed = all.length();
            } else if (all.length() < printed) {
                // Truncated / rotated
                spec.commandLine().getOut().print(all);
                spec.commandLine().getOut().flush();
                printed = all.length();
            }
        }
        return 0;
    }

    private io.xion.infrastructure.ipc.IpcEnvelope sendLogs(int tailLines) throws Exception {
        ObjectNode payload = client.object()
                .put("id", id)
                .put("stderr", stderr)
                .put("tail", tailLines)
                .put("follow", false);
        return client.send("logs", payload);
    }
}
