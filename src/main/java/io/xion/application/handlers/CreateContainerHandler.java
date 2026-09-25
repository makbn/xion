package io.xion.application.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.xion.application.mediator.RequestHandler;
import io.xion.domain.ContainerRecord;
import io.xion.domain.ContainerStatus;
import io.xion.domain.PortMapping;
import io.xion.domain.ResourceLimits;
import io.xion.domain.RestartPolicy;
import io.xion.domain.SandboxProfile;
import io.xion.domain.VolumeMount;
import io.xion.infrastructure.network.BridgeResolver;
import io.xion.infrastructure.process.RuntimePaths;
import io.xion.infrastructure.store.ContainerStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class CreateContainerHandler implements RequestHandler<CreateContainerCommand, CreateContainerResult> {

    private final ContainerStore store;
    private final RuntimePaths paths;
    private final BridgeResolver bridges;
    private final ObjectMapper mapper;

    @Inject
    public CreateContainerHandler(
            ContainerStore store,
            RuntimePaths paths,
            BridgeResolver bridges,
            ObjectMapper mapper) {
        this.store = store;
        this.paths = paths;
        this.bridges = bridges;
        this.mapper = mapper;
    }

    @Override
    public Class<CreateContainerCommand> requestType() {
        return CreateContainerCommand.class;
    }

    @Override
    public CreateContainerResult handle(CreateContainerCommand request) {
        String name = request.name() == null || request.name().isBlank()
                ? "xion-" + UUID.randomUUID().toString().substring(0, 8)
                : request.name();
        store.findByName(name).ifPresent(existing -> {
            throw new IllegalArgumentException("Container name already exists: " + name);
        });
        String id = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String runtimeDir = paths.containerDir(id).toString();
        ResourceLimits limits = request.limits() == null ? ResourceLimits.unlimited() : request.limits();
        List<VolumeMount> volumes = request.volumes() == null ? List.of() : request.volumes();
        List<PortMapping> ports = request.ports() == null ? List.of() : request.ports();
        List<String> args = request.args() == null ? List.of() : request.args();
        Optional<String> network = request.network() == null ? Optional.empty() : request.network();
        Map<String, String> env = request.env() == null ? Map.of() : request.env();
        Optional<String> workdir = request.workdir() == null ? Optional.empty() : request.workdir();
        boolean autoRemove = request.autoRemove();
        RestartPolicy restart = request.restartPolicy() == null ? RestartPolicy.NO : request.restartPolicy();
        SandboxProfile sandbox = request.sandboxProfile() == null
                ? SandboxProfile.STRICT
                : request.sandboxProfile();
        List<String> writablePaths = request.writablePaths() == null ? List.of() : request.writablePaths();

        ObjectNode json = mapper.createObjectNode();
        json.put("id", id);
        json.put("name", name);
        json.put("binary", request.binary());
        json.put("runtimeDir", runtimeDir);
        ArrayNode argsNode = json.putArray("args");
        args.forEach(argsNode::add);
        ArrayNode volumesNode = json.putArray("volumes");
        for (VolumeMount v : volumes) {
            ObjectNode vn = volumesNode.addObject();
            vn.put("hostPath", v.hostPath());
            vn.put("containerPath", v.containerPath());
            vn.put("readOnly", v.readOnly());
        }
        ArrayNode portsNode = json.putArray("ports");
        for (PortMapping p : ports) {
            ObjectNode pn = portsNode.addObject();
            pn.put("hostPort", p.hostPort());
            pn.put("containerPort", p.containerPort());
            pn.put("protocol", p.protocol());
        }
        if (network.isPresent()) {
            json.put("network", network.get());
        } else {
            json.putNull("network");
        }
        ObjectNode limitsNode = json.putObject("limits");
        if (limits.memoryBytes().isPresent()) {
            limitsNode.put("memoryBytes", limits.memoryBytes().get());
        } else {
            limitsNode.putNull("memoryBytes");
        }
        if (limits.cpus().isPresent()) {
            limitsNode.put("cpus", limits.cpus().get());
        } else {
            limitsNode.putNull("cpus");
        }
        ObjectNode envNode = json.putObject("env");
        env.forEach(envNode::put);
        if (workdir.isPresent()) {
            json.put("workdir", workdir.get());
        } else {
            json.putNull("workdir");
        }
        json.put("autoRemove", autoRemove);
        json.put("restartPolicy", restart.wire());
        json.put("sandboxProfile", sandbox.wire());
        ArrayNode writableNode = json.putArray("writablePaths");
        writablePaths.forEach(writableNode::add);

        network.ifPresent(bridges::createNetwork);
        ContainerRecord record = new ContainerRecord(
                id,
                name,
                request.binary(),
                ContainerStatus.CREATED,
                runtimeDir,
                Optional.empty(),
                network,
                Instant.now(),
                Optional.empty(),
                Optional.empty(),
                json.toString());
        store.save(record);
        return new CreateContainerResult(id, name, ContainerStatus.CREATED.name());
    }
}
