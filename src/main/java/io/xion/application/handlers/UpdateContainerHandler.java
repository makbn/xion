package io.xion.application.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.xion.application.mediator.RequestHandler;
import io.xion.domain.ContainerRecord;
import io.xion.domain.ContainerStatus;
import io.xion.domain.ResourceLimits;
import io.xion.infrastructure.network.BridgeResolver;
import io.xion.infrastructure.store.ContainerStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class UpdateContainerHandler implements RequestHandler<UpdateContainerCommand, UpdateContainerResult> {

    private final ContainerStore store;
    private final BridgeResolver bridges;
    private final ObjectMapper mapper;

    @Inject
    public UpdateContainerHandler(ContainerStore store, BridgeResolver bridges, ObjectMapper mapper) {
        this.store = store;
        this.bridges = bridges;
        this.mapper = mapper;
    }

    @Override
    public Class<UpdateContainerCommand> requestType() {
        return UpdateContainerCommand.class;
    }

    @Override
    public UpdateContainerResult handle(UpdateContainerCommand request) {
        ContainerRecord record = store.findById(request.idOrName())
                .or(() -> store.findByName(request.idOrName()))
                .orElseThrow(() -> new IllegalArgumentException("Container not found: " + request.idOrName()));

        boolean resourcesChanged = request.memory().isPresent() || request.cpus().isPresent();
        boolean networkChanged = request.disconnectNetwork() || request.network().isPresent();
        if (!resourcesChanged && !networkChanged) {
            throw new IllegalArgumentException(
                    "Nothing to update; pass --memory, --cpus, --network, and/or --network-none");
        }

        List<String> notes = new ArrayList<>();
        ObjectNode profile;
        try {
            JsonNode node = mapper.readTree(record.profileJson());
            profile = node.isObject() ? (ObjectNode) node : mapper.createObjectNode();
        } catch (Exception e) {
            profile = mapper.createObjectNode();
        }

        if (resourcesChanged) {
            ObjectNode limits = profile.has("limits") && profile.get("limits").isObject()
                    ? (ObjectNode) profile.get("limits")
                    : profile.putObject("limits");
            Long memBytes = null;
            if (request.memory().isPresent()) {
                memBytes = ResourceLimits.parseMemory(request.memory().get());
                limits.put("memoryBytes", memBytes);
                notes.add("memory → " + request.memory().get() + " (" + memBytes + " bytes)");
            }
            if (request.cpus().isPresent()) {
                limits.put("cpus", request.cpus().get());
                notes.add("cpus → " + request.cpus().get());
            }
            // Keep textual memory for IPC recreate paths
            if (request.memory().isPresent()) {
                profile.put("memory", request.memory().get());
            }
        }

        ContainerRecord updated = record;
        if (request.disconnectNetwork()) {
            bridges.detach(record.name());
            profile.putNull("network");
            updated = updated.withNetwork(null);
            notes.add("detached from network");
        } else if (request.network().isPresent()) {
            String net = request.network().get();
            bridges.createNetwork(net);
            bridges.detach(record.name());
            if (record.status() == ContainerStatus.RUNNING) {
                String endpoint = "127.0.0.1:0";
                try {
                    JsonNode ports = profile.path("ports");
                    if (ports.isArray() && !ports.isEmpty()) {
                        endpoint = "127.0.0.1:" + ports.get(0).path("containerPort").asInt(0);
                    }
                } catch (Exception ignored) {
                }
                bridges.attach(net, record.name(), endpoint);
                notes.add("reattached to network " + net + " (live)");
            } else {
                notes.add("network → " + net + " (applies on next start)");
            }
            profile.put("network", net);
            updated = updated.withNetwork(net);
        }

        updated = updated.withProfileJson(profile.toString());
        store.update(updated);

        boolean restartRequired = resourcesChanged && record.status() == ContainerStatus.RUNNING;
        if (restartRequired) {
            notes.add("resource limits apply on next start/restart (not live)");
        }
        String message = String.join("; ", notes);
        return new UpdateContainerResult(
                updated.id(),
                updated.name(),
                updated.status().name(),
                message,
                restartRequired);
    }
}
