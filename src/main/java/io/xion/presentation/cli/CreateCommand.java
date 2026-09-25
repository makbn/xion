package io.xion.presentation.cli;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.inject.Inject;
import picocli.CommandLine;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "create",
        mixinStandardHelpOptions = true,
        showDefaultValues = true,
        synopsisHeading = "%nUsage:%n  ",
        descriptionHeading = "%nDescription:%n%n",
        optionListHeading = "%nOptions:%n%n",
        parameterListHeading = "%nArguments:%n%n",
        footerHeading = "%nExamples:%n%n",
        description = {
                "Create a container record without starting it.",
                "",
                "Persists profile JSON (binary, args, volumes, ports, network, limits) in SQLite.",
                "Start later with `xion start ID|NAME`. Same option set as `xion run`.",
                "",
                "Requires a running daemon."
        },
        footer = {
                "  xion create --name batch -m 1g -- /usr/bin/compress /data/in",
                "  xion start batch"
        })
public class CreateCommand implements Callable<Integer> {

    @Inject
    DaemonClientSupport client;

    @CommandLine.Option(names = {"--name", "-n"}, paramLabel = "NAME",
            description = "Container name (unique). Auto-generated if omitted.")
    String name;

    @CommandLine.Option(names = {"-v", "--volume"}, paramLabel = "HOST:CONTAINER[:ro]", split = ",",
            description = "Bind mount allowed in the Seatbelt profile. Repeatable.")
    List<String> volumes = new ArrayList<>();

    @CommandLine.Option(names = {"-p", "--publish"}, paramLabel = "HOST:CONTAINER[/PROTO]", split = ",",
            description = "Port mapping recorded for start-time NIO proxy. Repeatable.")
    List<String> ports = new ArrayList<>();

    @CommandLine.Option(names = {"--network"}, paramLabel = "NAME",
            description = "Bridge network to attach when started.")
    String network;

    @CommandLine.Option(names = {"--memory", "-m"}, paramLabel = "LIMIT",
            description = "Memory limit string (512m, 1g, …).")
    String memory;

    @CommandLine.Option(names = {"--cpus"}, paramLabel = "FLOAT",
            description = "CPU limit hint.")
    Double cpus;

    @CommandLine.Parameters(index = "0", paramLabel = "BINARY",
            description = "Host executable path or PATH name.")
    String binary;

    @CommandLine.Parameters(index = "1..*", paramLabel = "ARG", arity = "0..*",
            description = "Arguments for BINARY.")
    List<String> args = new ArrayList<>();

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public Integer call() throws Exception {
        ObjectNode create = client.object();
        if (name != null) {
            create.put("name", name);
        }
        create.put("binary", binary);
        ArrayNode argsNode = create.putArray("args");
        args.forEach(argsNode::add);
        ArrayNode volumesNode = create.putArray("volumes");
        volumes.forEach(volumesNode::add);
        ArrayNode portsNode = create.putArray("ports");
        ports.forEach(portsNode::add);
        if (network != null) {
            create.put("network", network);
        }
        if (memory != null) {
            create.put("memory", memory);
        }
        if (cpus != null) {
            create.put("cpus", cpus);
        }
        var created = client.send("create", create);
        if (!created.ok()) {
            spec.commandLine().getErr().println(created.error());
            return 1;
        }
        spec.commandLine().getOut().println(created.payload().get("id").asText());
        return 0;
    }
}
