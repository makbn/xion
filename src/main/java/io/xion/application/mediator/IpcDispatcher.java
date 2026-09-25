package io.xion.application.mediator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.xion.application.handlers.CreateContainerCommand;
import io.xion.application.handlers.CreateContainerResult;
import io.xion.application.handlers.CreateNetworkCommand;
import io.xion.application.handlers.CreateNetworkResult;
import io.xion.application.handlers.InspectContainerQuery;
import io.xion.application.handlers.InspectContainerResult;
import io.xion.application.handlers.InspectNetworkQuery;
import io.xion.application.handlers.InspectNetworkResult;
import io.xion.application.handlers.ListContainersQuery;
import io.xion.application.handlers.ListContainersResult;
import io.xion.application.handlers.ListNetworksQuery;
import io.xion.application.handlers.ListNetworksResult;
import io.xion.application.handlers.LogsQuery;
import io.xion.application.handlers.LogsResult;
import io.xion.application.handlers.RemoveContainerCommand;
import io.xion.application.handlers.RemoveContainerResult;
import io.xion.application.handlers.RemoveNetworkCommand;
import io.xion.application.handlers.RemoveNetworkResult;
import io.xion.application.handlers.StartContainerCommand;
import io.xion.application.handlers.StartContainerResult;
import io.xion.application.handlers.StopContainerCommand;
import io.xion.application.handlers.StopContainerResult;
import io.xion.application.handlers.UpdateContainerCommand;
import io.xion.application.handlers.UpdateContainerResult;
import io.xion.domain.ContainerRecord;
import io.xion.domain.PortMapping;
import io.xion.domain.ResourceLimits;
import io.xion.domain.RestartPolicy;
import io.xion.domain.SandboxProfile;
import io.xion.domain.VolumeMount;
import io.xion.infrastructure.ipc.IpcEnvelope;
import io.xion.infrastructure.process.EnvFileParser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Maps UDS IPC envelopes onto Mediator requests.
 */
@ApplicationScoped
public class IpcDispatcher {

    private final Mediator mediator;
    private final ObjectMapper mapper;

    @Inject
    public IpcDispatcher(Mediator mediator, ObjectMapper mapper) {
        this.mediator = mediator;
        this.mapper = mapper;
    }

    public IpcEnvelope dispatch(IpcEnvelope request) {
        try {
            JsonNode payload = request.payload() == null ? mapper.createObjectNode() : request.payload();
            Object result = switch (request.type()) {
                case "create" -> mediator.send(toCreate(payload));
                case "start" -> mediator.send(new StartContainerCommand(text(payload, "id")));
                case "stop" -> mediator.send(new StopContainerCommand(text(payload, "id")));
                case "logs" -> mediator.send(new LogsQuery(
                        text(payload, "id"),
                        payload.path("stderr").asBoolean(false),
                        payload.path("tail").asInt(0),
                        payload.path("follow").asBoolean(false)));
                case "ps", "list" -> mediator.send(new ListContainersQuery(payload.path("all").asBoolean(false)));
                case "inspect" -> mediator.send(new InspectContainerQuery(text(payload, "id")));
                case "rm", "remove" -> mediator.send(new RemoveContainerCommand(
                        text(payload, "id"), payload.path("force").asBoolean(false)));
                case "update" -> mediator.send(toUpdate(payload));
                case "network.create" -> mediator.send(new CreateNetworkCommand(
                        text(payload, "name"),
                        payload.hasNonNull("subnet") ? payload.get("subnet").asText() : null));
                case "network.ls", "network.list" -> mediator.send(new ListNetworksQuery());
                case "network.rm", "network.remove" -> mediator.send(new RemoveNetworkCommand(
                        text(payload, "name"), payload.path("force").asBoolean(false)));
                case "network.inspect" -> mediator.send(new InspectNetworkQuery(text(payload, "name")));
                case "ping" -> mapper.createObjectNode().put("pong", true);
                default -> throw new IllegalArgumentException("Unknown command: " + request.type());
            };
            return IpcEnvelope.success(request.type(), request.requestId(), toJson(result));
        } catch (Exception e) {
            return IpcEnvelope.failure(request.type(), request.requestId(), e.getMessage());
        }
    }

    private UpdateContainerCommand toUpdate(JsonNode payload) {
        Optional<String> memory = payload.hasNonNull("memory")
                ? Optional.of(payload.get("memory").asText())
                : Optional.empty();
        Optional<Double> cpus = payload.hasNonNull("cpus")
                ? Optional.of(payload.get("cpus").asDouble())
                : Optional.empty();
        Optional<String> network = payload.hasNonNull("network")
                ? Optional.of(payload.get("network").asText())
                : Optional.empty();
        boolean disconnect = payload.path("disconnectNetwork").asBoolean(false)
                || payload.path("networkNone").asBoolean(false);
        return new UpdateContainerCommand(text(payload, "id"), memory, cpus, network, disconnect);
    }

    private CreateContainerCommand toCreate(JsonNode payload) {
        List<String> args = new ArrayList<>();
        payload.path("args").forEach(n -> args.add(n.asText()));
        List<VolumeMount> volumes = new ArrayList<>();
        payload.path("volumes").forEach(n -> {
            if (n.isTextual()) {
                volumes.add(VolumeMount.parse(n.asText()));
            } else {
                volumes.add(new VolumeMount(
                        n.get("hostPath").asText(),
                        n.get("containerPath").asText(),
                        n.path("readOnly").asBoolean(false)));
            }
        });
        List<PortMapping> ports = new ArrayList<>();
        payload.path("ports").forEach(n -> {
            if (n.isTextual()) {
                ports.add(PortMapping.parse(n.asText()));
            } else {
                ports.add(new PortMapping(
                        n.get("hostPort").asInt(),
                        n.get("containerPort").asInt(),
                        n.path("protocol").asText("tcp")));
            }
        });
        Optional<String> network = payload.hasNonNull("network")
                ? Optional.of(payload.get("network").asText())
                : Optional.empty();
        String memory = payload.path("memory").asText(null);
        Double cpus = payload.hasNonNull("cpus") ? payload.get("cpus").asDouble() : null;
        ResourceLimits limits = ResourceLimits.of(memory, cpus);

        Map<String, String> fromFiles = new LinkedHashMap<>();
        payload.path("envFiles").forEach(n -> fromFiles.putAll(EnvFileParser.parse(Path.of(n.asText()))));
        Map<String, String> fromEnv = new LinkedHashMap<>();
        JsonNode envNode = payload.get("env");
        if (envNode != null && envNode.isObject()) {
            envNode.fields().forEachRemaining(e -> fromEnv.put(e.getKey(), e.getValue().asText("")));
        } else if (envNode != null && envNode.isArray()) {
            envNode.forEach(n -> fromEnv.putAll(EnvFileParser.parseAssignment(n.asText())));
        }
        Map<String, String> env = EnvFileParser.merge(fromFiles, fromEnv);

        Optional<String> workdir = payload.hasNonNull("workdir")
                ? Optional.of(payload.get("workdir").asText())
                : Optional.empty();
        boolean autoRemove = payload.path("autoRemove").asBoolean(false)
                || payload.path("rm").asBoolean(false);
        RestartPolicy restartPolicy = RestartPolicy.NO;
        if (payload.hasNonNull("restartPolicy")) {
            restartPolicy = RestartPolicy.parse(payload.get("restartPolicy").asText());
        } else if (payload.hasNonNull("restart")) {
            restartPolicy = RestartPolicy.parse(payload.get("restart").asText());
        }
        SandboxProfile sandboxProfile = SandboxProfile.STRICT;
        if (payload.hasNonNull("sandboxProfile")) {
            sandboxProfile = SandboxProfile.parse(payload.get("sandboxProfile").asText());
        } else if (payload.hasNonNull("sandbox")) {
            sandboxProfile = SandboxProfile.parse(payload.get("sandbox").asText());
        }
        List<String> writablePaths = new ArrayList<>();
        payload.path("writablePaths").forEach(n -> writablePaths.add(n.asText()));
        payload.path("writablePath").forEach(n -> writablePaths.add(n.asText()));

        return new CreateContainerCommand(
                payload.path("name").asText(null),
                text(payload, "binary"),
                args,
                volumes,
                ports,
                network,
                limits,
                env,
                workdir,
                autoRemove,
                restartPolicy,
                sandboxProfile,
                writablePaths);
    }

    private JsonNode toJson(Object result) {
        if (result instanceof JsonNode node) {
            return node;
        }
        if (result instanceof CreateContainerResult r) {
            return mapper.createObjectNode()
                    .put("id", r.id())
                    .put("name", r.name())
                    .put("status", r.status());
        }
        if (result instanceof StartContainerResult r) {
            return mapper.createObjectNode()
                    .put("id", r.id())
                    .put("name", r.name())
                    .put("pid", r.pid())
                    .put("status", r.status());
        }
        if (result instanceof StopContainerResult r) {
            return mapper.createObjectNode()
                    .put("id", r.id())
                    .put("name", r.name())
                    .put("status", r.status());
        }
        if (result instanceof LogsResult r) {
            return mapper.createObjectNode()
                    .put("id", r.id())
                    .put("content", r.content());
        }
        if (result instanceof CreateNetworkResult r) {
            ObjectNode n = mapper.createObjectNode().put("name", r.name());
            if (r.subnet() != null) {
                n.put("subnet", r.subnet());
            }
            if (r.gateway() != null) {
                n.put("gateway", r.gateway());
            }
            return n;
        }
        if (result instanceof RemoveNetworkResult r) {
            return mapper.createObjectNode().put("name", r.name()).put("removed", r.removed());
        }
        if (result instanceof RemoveContainerResult r) {
            return mapper.createObjectNode()
                    .put("id", r.id())
                    .put("name", r.name())
                    .put("removed", r.removed());
        }
        if (result instanceof UpdateContainerResult r) {
            return mapper.createObjectNode()
                    .put("id", r.id())
                    .put("name", r.name())
                    .put("status", r.status())
                    .put("message", r.message())
                    .put("restartRequired", r.restartRequired());
        }
        if (result instanceof InspectContainerResult r) {
            return containerNode(r.container());
        }
        if (result instanceof InspectNetworkResult r) {
            ObjectNode root = mapper.createObjectNode();
            root.put("name", r.name());
            if (r.subnet() != null) {
                root.put("subnet", r.subnet());
            }
            if (r.gateway() != null) {
                root.put("gateway", r.gateway());
            }
            ArrayNode members = root.putArray("members");
            r.members().forEach(members::add);
            ObjectNode eps = root.putObject("endpoints");
            for (Map.Entry<String, String> e : r.endpoints().entrySet()) {
                eps.put(e.getKey(), e.getValue());
            }
            ObjectNode ips = root.putObject("memberIps");
            if (r.memberIps() != null) {
                for (Map.Entry<String, String> e : r.memberIps().entrySet()) {
                    ips.put(e.getKey(), e.getValue());
                }
            }
            return root;
        }
        if (result instanceof ListNetworksResult r) {
            ObjectNode root = mapper.createObjectNode();
            ArrayNode arr = root.putArray("networks");
            for (ListNetworksResult.NetworkInfo n : r.networks()) {
                ObjectNode o = arr.addObject();
                o.put("name", n.name());
                if (n.subnet() != null) {
                    o.put("subnet", n.subnet());
                }
                if (n.gateway() != null) {
                    o.put("gateway", n.gateway());
                }
                o.put("memberCount", n.memberCount());
                ArrayNode members = o.putArray("members");
                n.members().forEach(members::add);
            }
            return root;
        }
        if (result instanceof ListContainersResult r) {
            ObjectNode root = mapper.createObjectNode();
            ArrayNode arr = root.putArray("containers");
            for (ContainerRecord c : r.containers()) {
                arr.add(containerNode(c));
            }
            return root;
        }
        return mapper.valueToTree(result);
    }

    private ObjectNode containerNode(ContainerRecord c) {
        ObjectNode n = mapper.createObjectNode();
        n.put("id", c.id());
        n.put("name", c.name());
        n.put("binary", c.binary());
        n.put("status", c.status().name());
        n.put("runtimeDir", c.runtimeDir());
        n.put("createdAt", c.createdAt().toString());
        c.pid().ifPresent(pid -> n.put("pid", pid));
        c.network().ifPresent(net -> n.put("network", net));
        c.startedAt().ifPresent(t -> n.put("startedAt", t.toString()));
        c.stoppedAt().ifPresent(t -> n.put("stoppedAt", t.toString()));
        try {
            n.set("profile", mapper.readTree(c.profileJson()));
        } catch (Exception e) {
            n.put("profileJson", c.profileJson());
        }
        return n;
    }

    private static String text(JsonNode payload, String field) {
        JsonNode n = payload.get(field);
        if (n == null || n.isNull() || n.asText().isBlank()) {
            throw new IllegalArgumentException("Missing required field: " + field);
        }
        return n.asText();
    }
}
