package io.xion.presentation.cli;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.inject.Inject;
import picocli.CommandLine;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

@CommandLine.Command(name = "create", description = "Create a container without starting it")
public class CreateCommand implements Callable<Integer> {

    @Inject
    DaemonClientSupport client;

    @CommandLine.Option(names = {"--name", "-n"})
    String name;

    @CommandLine.Option(names = {"-v"}, split = ",")
    List<String> volumes = new ArrayList<>();

    @CommandLine.Option(names = {"-p"}, split = ",")
    List<String> ports = new ArrayList<>();

    @CommandLine.Option(names = {"--network"})
    String network;

    @CommandLine.Option(names = {"--memory", "-m"})
    String memory;

    @CommandLine.Option(names = {"--cpus"})
    Double cpus;

    @CommandLine.Parameters(index = "0")
    String binary;

    @CommandLine.Parameters(index = "1..*", arity = "0..*")
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
