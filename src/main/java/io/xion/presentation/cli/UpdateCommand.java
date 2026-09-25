package io.xion.presentation.cli;

import jakarta.inject.Inject;
import picocli.CommandLine;

import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "update",
        mixinStandardHelpOptions = true,
        synopsisHeading = "%nUsage:%n  ",
        descriptionHeading = "%nDescription:%n%n",
        optionListHeading = "%nOptions:%n%n",
        parameterListHeading = "%nArguments:%n%n",
        footerHeading = "%nExamples:%n%n",
        description = {
                "Update a container's resource limits and/or bridge network.",
                "",
                "--memory / --cpus are written into the stored profile and applied on the",
                "next start/restart (not live on a running process).",
                "",
                "--network reconnects the bridge membership immediately if the container is",
                "RUNNING; otherwise it is applied on next start. --network-none detaches.",
                "",
                "Requires a running daemon."
        },
        footer = {
                "  xion update web --memory 512m --cpus 1.5",
                "  xion update web --network frontend",
                "  xion update web --network-none",
                "  xion stop web && xion start web   # apply new memory/cpus"
        })
public class UpdateCommand implements Callable<Integer> {

    @Inject
    DaemonClientSupport client;

    @CommandLine.Option(names = {"--memory", "-m"}, paramLabel = "LIMIT",
            description = "New memory limit (e.g. 512m). Applied on next start.")
    String memory;

    @CommandLine.Option(names = {"--cpus"}, paramLabel = "FLOAT",
            description = "New CPU limit. Applied on next start.")
    Double cpus;

    @CommandLine.Option(names = {"--network"}, paramLabel = "NAME",
            description = "Attach/move to bridge network NAME.")
    String network;

    @CommandLine.Option(names = {"--network-none"},
            description = "Detach from any bridge network.")
    boolean networkNone;

    @CommandLine.Parameters(index = "0", paramLabel = "CONTAINER",
            description = "Container id or name.")
    String id;

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public Integer call() throws Exception {
        if (memory == null && cpus == null && network == null && !networkNone) {
            spec.commandLine().getErr().println(
                    "Nothing to update; pass --memory, --cpus, --network, and/or --network-none");
            return 1;
        }
        if (network != null && networkNone) {
            spec.commandLine().getErr().println("Use either --network or --network-none, not both");
            return 1;
        }
        var payload = client.object().put("id", id);
        if (memory != null) {
            payload.put("memory", memory);
        }
        if (cpus != null) {
            payload.put("cpus", cpus);
        }
        if (network != null) {
            payload.put("network", network);
        }
        if (networkNone) {
            payload.put("disconnectNetwork", true);
        }
        var response = client.send("update", payload);
        if (!response.ok()) {
            spec.commandLine().getErr().println(response.error());
            return 1;
        }
        var out = spec.commandLine().getOut();
        out.println(response.payload().path("id").asText());
        String msg = response.payload().path("message").asText("");
        if (!msg.isBlank()) {
            out.println(msg);
        }
        if (response.payload().path("restartRequired").asBoolean(false)) {
            out.println("Note: restart the container to apply resource limits.");
        }
        return 0;
    }
}
