package io.xion.application.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.xion.application.mediator.RequestHandler;
import io.xion.domain.ContainerProfile;
import io.xion.domain.ContainerRecord;
import io.xion.domain.ContainerStatus;
import io.xion.domain.PortMapping;
import io.xion.domain.ResourceLimits;
import io.xion.domain.VolumeMount;
import io.xion.infrastructure.network.BridgeResolver;
import io.xion.infrastructure.network.PortProxy;
import io.xion.infrastructure.process.ProcessRegistry;
import io.xion.infrastructure.process.RuntimePaths;
import io.xion.infrastructure.resources.ResourceGovernor;
import io.xion.infrastructure.seatbelt.SandboxExecutor;
import io.xion.infrastructure.seatbelt.SeatbeltProfileGenerator;
import io.xion.infrastructure.store.ContainerStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class StartContainerHandler implements RequestHandler<StartContainerCommand, StartContainerResult> {

    private final ContainerStore store;
    private final RuntimePaths paths;
    private final SeatbeltProfileGenerator profileGenerator;
    private final SandboxExecutor sandboxExecutor;
    private final ResourceGovernor resourceGovernor;
    private final BridgeResolver bridges;
    private final ProcessRegistry processRegistry;
    private final ObjectMapper mapper;

    @Inject
    public StartContainerHandler(
            ContainerStore store,
            RuntimePaths paths,
            SeatbeltProfileGenerator profileGenerator,
            SandboxExecutor sandboxExecutor,
            ResourceGovernor resourceGovernor,
            BridgeResolver bridges,
            ProcessRegistry processRegistry,
            ObjectMapper mapper) {
        this.store = store;
        this.paths = paths;
        this.profileGenerator = profileGenerator;
        this.sandboxExecutor = sandboxExecutor;
        this.resourceGovernor = resourceGovernor;
        this.bridges = bridges;
        this.processRegistry = processRegistry;
        this.mapper = mapper;
    }

    @Override
    public Class<StartContainerCommand> requestType() {
        return StartContainerCommand.class;
    }

    @Override
    public StartContainerResult handle(StartContainerCommand request) {
        ContainerRecord record = resolve(request.idOrName());
        if (record.status() == ContainerStatus.RUNNING) {
            throw new IllegalStateException("Container already running: " + record.id());
        }
        ContainerProfile profile = deserialize(record);
        String allocatedIp = null;
        try {
            Files.createDirectories(paths.containerDir(record.id()));

            int firstContainerPort = profile.ports().isEmpty() ? 0 : profile.ports().getFirst().containerPort();
            int proxyPort = profile.ports().isEmpty() ? 0 : profile.ports().getFirst().hostPort();

            if (profile.network().isPresent()) {
                String net = profile.network().get();
                bridges.createNetwork(net);
                allocatedIp = bridges.attachWithIp(net, profile.name(), firstContainerPort);
            }

            String targetHost = allocatedIp != null ? allocatedIp : "127.0.0.1";
            Path sb = profileGenerator.writeProfile(profile, proxyPort, allocatedIp);

            resourceGovernor.apply(profile.limits());

            List<String> command = new ArrayList<>();
            command.add(profile.binary());
            command.addAll(profile.args());
            List<String> wrapped = resourceGovernor.wrapCommand(command, profile.limits());
            String binary = wrapped.getFirst();
            List<String> args = wrapped.size() > 1 ? wrapped.subList(1, wrapped.size()) : List.of();

            Map<String, String> env = new HashMap<>();
            if (allocatedIp != null) {
                env.put("XION_IP", allocatedIp);
                profile.network().ifPresent(n -> env.put("XION_NETWORK", n));
            }
            if (firstContainerPort > 0) {
                env.put("PORT", Integer.toString(firstContainerPort));
            }

            SandboxExecutor.SpawnedProcess spawned = sandboxExecutor.spawn(
                    sb,
                    binary,
                    args,
                    paths.containerDir(record.id()),
                    paths.stdoutLog(record.id()),
                    paths.stderrLog(record.id()),
                    env);

            processRegistry.register(record.id(), spawned.process());

            if (!profile.ports().isEmpty()) {
                PortProxy proxy = new PortProxy();
                proxy.start(profile.ports(), targetHost);
                processRegistry.registerProxy(record.id(), proxy);
            }

            // Persist allocated IP into profile for inspect
            ContainerRecord updated = record
                    .withStatus(ContainerStatus.RUNNING)
                    .withPid(spawned.pid())
                    .withStartedAt(Instant.now());
            if (allocatedIp != null) {
                updated = updated.withProfileJson(withIp(record.profileJson(), allocatedIp));
            }
            store.update(updated);
            return new StartContainerResult(record.id(), record.name(), spawned.pid(), ContainerStatus.RUNNING.name());
        } catch (Exception e) {
            if (allocatedIp != null) {
                bridges.detach(profile.name());
            }
            throw new IllegalStateException("Failed to start container: " + e.getMessage(), e);
        }
    }

    private String withIp(String profileJson, String ip) {
        try {
            ObjectNode node = (ObjectNode) mapper.readTree(profileJson);
            node.put("ip", ip);
            return node.toString();
        } catch (Exception e) {
            return profileJson;
        }
    }

    private ContainerRecord resolve(String idOrName) {
        return store.findById(idOrName)
                .or(() -> store.findByName(idOrName))
                .orElseThrow(() -> new IllegalArgumentException("Container not found: " + idOrName));
    }

    private ContainerProfile deserialize(ContainerRecord record) {
        try {
            var node = mapper.readTree(record.profileJson());
            List<VolumeMount> volumes = new ArrayList<>();
            if (node.has("volumes")) {
                for (var v : node.get("volumes")) {
                    volumes.add(new VolumeMount(
                            v.get("hostPath").asText(),
                            v.get("containerPath").asText(),
                            v.path("readOnly").asBoolean(false)));
                }
            }
            List<PortMapping> ports = new ArrayList<>();
            if (node.has("ports")) {
                for (var p : node.get("ports")) {
                    ports.add(new PortMapping(
                            p.get("hostPort").asInt(),
                            p.get("containerPort").asInt(),
                            p.path("protocol").asText("tcp")));
                }
            }
            List<String> args = new ArrayList<>();
            if (node.has("args")) {
                node.get("args").forEach(a -> args.add(a.asText()));
            }
            Optional<String> network = node.has("network") && !node.get("network").isNull()
                    ? Optional.of(node.get("network").asText())
                    : record.network();
            ResourceLimits limits = ResourceLimits.unlimited();
            if (node.has("limits")) {
                var lim = node.get("limits");
                Long mem = lim.has("memoryBytes") && !lim.get("memoryBytes").isNull()
                        ? lim.get("memoryBytes").asLong()
                        : null;
                Double cpus = lim.has("cpus") && !lim.get("cpus").isNull()
                        ? lim.get("cpus").asDouble()
                        : null;
                limits = new ResourceLimits(
                        Optional.ofNullable(mem),
                        Optional.ofNullable(cpus));
            }
            String binary = node.has("binary") ? node.get("binary").asText() : record.binary();
            return new ContainerProfile(
                    record.id(),
                    record.name(),
                    binary,
                    args,
                    volumes,
                    ports,
                    network,
                    limits,
                    record.runtimeDir());
        } catch (Exception e) {
            return new ContainerProfile(
                    record.id(),
                    record.name(),
                    record.binary(),
                    List.of(),
                    List.of(),
                    List.of(),
                    record.network(),
                    ResourceLimits.unlimited(),
                    record.runtimeDir());
        }
    }
}
