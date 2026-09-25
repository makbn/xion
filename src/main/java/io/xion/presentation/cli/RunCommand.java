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
        mixinStandardHelpOptions = true,
        showDefaultValues = true,
        synopsisHeading = "%nUsage:%n  ",
        descriptionHeading = "%nDescription:%n%n",
        optionListHeading = "%nOptions:%n%n",
        parameterListHeading = "%nArguments:%n%n",
        footerHeading = "%nExamples:%n%n",
        description = {
                "Create a container profile and immediately start it (create + start).",
                "",
                "The daemon writes a Seatbelt profile, applies resource limits, optionally",
                "starts a host→container port proxy, then spawns the binary via sandbox-exec",
                "(macOS) or a direct process (Linux CI / fake executor).",
                "",
                "Unlike `docker run`, the first positional argument is a host binary path (or",
                "PATH name), not an OCI image. Use `xion docker` to translate Docker run lines.",
                "",
                "Requires a running daemon (`xion daemon start`)."
        },
        footer = {
                "  xion run --name web -p 8080:80 --memory 256m --cpus 1 -- /usr/bin/sleep 3600",
                "  xion run -v /host/data:/data:ro --network frontend -- /usr/local/bin/app",
                "  xion docker --yes --allow-partial -- run -d -p 8080:80 nginx"
        })
public class RunCommand implements Callable<Integer> {

    @Inject
    DaemonClientSupport client;

    @CommandLine.Option(
            names = {"--name", "-n"},
            paramLabel = "NAME",
            description = "Assign a name to the container (must be unique). Auto-generated if omitted.")
    String name;

    @CommandLine.Option(
            names = {"-v", "--volume"},
            paramLabel = "HOST:CONTAINER[:ro]",
            split = ",",
            description = {
                    "Bind-mount a host directory into the Seatbelt profile as an allowed subpath.",
                    "Repeatable. Append :ro for read-only (file-read* only)."
            })
    List<String> volumes = new ArrayList<>();

    @CommandLine.Option(
            names = {"-p", "--publish"},
            paramLabel = "HOST:CONTAINER[/PROTO]",
            split = ",",
            description = {
                    "Publish a container port on the host. The daemon listens on HOST and proxies",
                    "TCP to 127.0.0.1:CONTAINER inside the sandboxed process's loopback view.",
                    "Protocol defaults to tcp. Example: -p 8080:80 or -p 53:53/udp (udp ignored today)."
            })
    List<String> ports = new ArrayList<>();

    @CommandLine.Option(
            names = {"--network"},
            paramLabel = "NAME",
            description = "Attach to a named bridge network (created with `xion network create`). "
                    + "Enables hostname resolution between members (http://other-container).")
    String network;

    @CommandLine.Option(
            names = {"--memory", "-m"},
            paramLabel = "LIMIT",
            description = "Memory limit (e.g. 512m, 1g, 256Mi). Applied via setrlimit on Darwin.")
    String memory;

    @CommandLine.Option(
            names = {"--cpus"},
            paramLabel = "FLOAT",
            description = "CPU budget hint (e.g. 1.5). On macOS wraps spawn with taskpolicy and CPU rlimit.")
    Double cpus;

    @CommandLine.Parameters(
            index = "0",
            paramLabel = "BINARY",
            description = "Executable to run (absolute path recommended). Not an OCI image reference.")
    String binary;

    @CommandLine.Parameters(
            index = "1..*",
            paramLabel = "ARG",
            description = "Arguments passed to BINARY.",
            arity = "0..*")
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
