package io.xion.application.mediator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.xion.application.handlers.CreateContainerCommand;
import io.xion.application.handlers.CreateContainerResult;
import io.xion.application.handlers.CreateNetworkCommand;
import io.xion.application.handlers.CreateNetworkResult;
import io.xion.application.handlers.ListContainersQuery;
import io.xion.application.handlers.ListContainersResult;
import io.xion.application.handlers.LogsQuery;
import io.xion.application.handlers.LogsResult;
import io.xion.application.handlers.StartContainerCommand;
import io.xion.application.handlers.StartContainerResult;
import io.xion.application.handlers.StopContainerCommand;
import io.xion.application.handlers.StopContainerResult;
import io.xion.domain.ContainerRecord;
import io.xion.domain.PortMapping;
import io.xion.domain.ResourceLimits;
import io.xion.domain.VolumeMount;
import io.xion.infrastructure.ipc.IpcEnvelope;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
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
                case "logs" -> mediator.send(new LogsQuery(text(payload, "id"), payload.path("stderr").asBoolean(false)));
                case "ps", "list" -> mediator.send(new ListContainersQuery());
                case "network.create" -> mediator.send(new CreateNetworkCommand(text(payload, "name")));
                case "ping" -> mapper.createObjectNode().put("pong", true);
                default -> throw new IllegalArgumentException("Unknown command: " + request.type());
            };
            return IpcEnvelope.success(request.type(), request.requestId(), toJson(result));
        } catch (Exception e) {
            return IpcEnvelope.failure(request.type(), request.requestId(), e.getMessage());
        }
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
        return new CreateContainerCommand(
                payload.path("name").asText(null),
                text(payload, "binary"),
                args,
                volumes,
                ports,
                network,
                limits);
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
            return mapper.createObjectNode().put("name", r.name());
        }
        if (result instanceof ListContainersResult r) {
            ObjectNode root = mapper.createObjectNode();
            ArrayNode arr = root.putArray("containers");
            for (ContainerRecord c : r.containers()) {
                ObjectNode n = arr.addObject();
                n.put("id", c.id());
                n.put("name", c.name());
                n.put("binary", c.binary());
                n.put("status", c.status().name());
                c.pid().ifPresent(pid -> n.put("pid", pid));
                c.network().ifPresent(net -> n.put("network", net));
            }
            return root;
        }
        return mapper.valueToTree(result);
    }

    private static String text(JsonNode payload, String field) {
        JsonNode n = payload.get(field);
        if (n == null || n.isNull() || n.asText().isBlank()) {
            throw new IllegalArgumentException("Missing required field: " + field);
        }
        return n.asText();
    }
}
