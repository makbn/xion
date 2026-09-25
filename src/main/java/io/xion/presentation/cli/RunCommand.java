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
                "  xion run --sandbox-profile=relay -p 8080:8080 -- /bin/bash ./start.sh",
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
                    "Publish a container port on the host. The daemon listens on HOST and reverse-proxies",
                    "TCP to the container's assigned IP:CONTAINER (or 127.0.0.1 when not on a network).",
                    "Protocol defaults to tcp. Example: -p 9000:8087 or -p 53:53/udp (udp ignored today)."
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
            description = "Memory limit (e.g. 512m, 1g, 256Mi). Applied to the child only (not the daemon).")
    String memory;

    @CommandLine.Option(
            names = {"--cpus"},
            paramLabel = "FLOAT",
            description = "CPU budget hint (e.g. 1.5). On macOS wraps the child with /usr/sbin/taskpolicy.")
    Double cpus;

    @CommandLine.Option(
            names = {"-e", "--env"},
            paramLabel = "KEY=VALUE",
            description = "Set environment variable (repeatable). Overrides values from --env-file.")
    List<String> env = new ArrayList<>();

    @CommandLine.Option(
            names = {"--env-file"},
            paramLabel = "FILE",
            description = "Read env vars from a Docker-style env file (repeatable).")
    List<String> envFiles = new ArrayList<>();

    @CommandLine.Option(
            names = {"-w", "--workdir"},
            paramLabel = "DIR",
            description = "Working directory for the process (ProcessBuilder directory).")
    String workdir;

    @CommandLine.Option(
            names = {"--rm"},
            description = "Automatically remove the container when it exits.")
    boolean rm;

    @CommandLine.Option(
            names = {"--restart"},
            paramLabel = "POLICY",
            description = "Restart policy: no|on-failure[:N]|always|unless-stopped.")
    String restart;

    @CommandLine.Option(
            names = {"--sandbox-profile", "--sandbox"},
            paramLabel = "PROFILE",
            description = {
                    "Seatbelt preset: strict (default) or relay.",
                    "Use relay for trusted local apps that need host toolchain reads",
                    "(Node, Homebrew ffmpeg/VideoToolbox) and outbound internet.",
                    "Aliases: network-relay, devtools → relay."
            })
    String sandboxProfile;

    @CommandLine.Option(
            names = {"--writable-path"},
            paramLabel = "PATH",
            description = {
                    "Absolute host path to create (if missing) and allow writes under Seatbelt.",
                    "Use for Docker-style absolute data dirs that are not covered by -v.",
                    "Repeatable. Also configurable via xion.sandbox.extra-writable-paths."
            })
    List<String> writablePaths = new ArrayList<>();

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
        if (!env.isEmpty()) {
            ArrayNode envNode = create.putArray("env");
            env.forEach(envNode::add);
        }
        if (!envFiles.isEmpty()) {
            ArrayNode filesNode = create.putArray("envFiles");
            envFiles.forEach(filesNode::add);
        }
        if (workdir != null) {
            create.put("workdir", workdir);
        }
        if (rm) {
            create.put("autoRemove", true);
        }
        if (restart != null) {
            create.put("restartPolicy", restart);
        }
        if (sandboxProfile != null) {
            create.put("sandboxProfile", sandboxProfile);
        }
        if (!writablePaths.isEmpty()) {
            ArrayNode wp = create.putArray("writablePaths");
            writablePaths.forEach(wp::add);
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
