package io.xion.presentation.cli;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.inject.Inject;
import picocli.CommandLine;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "run",
        description = "Create and start a container (create + start)")
public class RunCommand implements Callable<Integer> {

    @Inject
    DaemonClientSupport client;

    @CommandLine.Option(names = {"--name", "-n"}, description = "Container name")
    String name;

    @CommandLine.Option(names = {"-v"}, description = "Bind mount host:container[:ro]", split = ",")
    List<String> volumes = new ArrayList<>();

    @CommandLine.Option(names = {"-p"}, description = "Publish host:container[/proto]", split = ",")
    List<String> ports = new ArrayList<>();

    @CommandLine.Option(names = {"--network"}, description = "Attach to bridge network")
    String network;

    @CommandLine.Option(names = {"--memory", "-m"}, description = "Memory limit (e.g. 512m)")
    String memory;

    @CommandLine.Option(names = {"--cpus"}, description = "CPU limit (e.g. 1.5)")
    Double cpus;

    @CommandLine.Parameters(index = "0", description = "Binary to run")
    String binary;

    @CommandLine.Parameters(index = "1..*", description = "Arguments", arity = "0..*")
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
            spec.commandLine().getErr().println("create failed: " + created.error());
            return 1;
        }
        String id = created.payload().get("id").asText();
        ObjectNode start = client.object().put("id", id);
        var started = client.send("start", start);
        if (!started.ok()) {
            spec.commandLine().getErr().println("start failed: " + started.error());
            return 1;
        }
        spec.commandLine().getOut().println(id);
        return 0;
    }
}
