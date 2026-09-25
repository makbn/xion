package io.xion.application.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import io.xion.application.mediator.DaemonService;
import io.xion.application.mediator.Mediator;
import io.xion.domain.ResourceLimits;
import io.xion.infrastructure.resources.FakeResourceGovernor;
import io.xion.infrastructure.seatbelt.FakeSandboxExecutor;
import io.xion.infrastructure.store.ContainerStore;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class LifecycleIntegrationTest {

    @Inject
    Mediator mediator;

    @Inject
    DaemonService daemonService;

    @Inject
    FakeSandboxExecutor fakeSandboxExecutor;

    @Inject
    FakeResourceGovernor fakeResourceGovernor;

    @Inject
    ContainerStore store;

    @Inject
    ObjectMapper mapper;

    @Inject
    io.xion.presentation.cli.DaemonClientSupport client;

    @Inject
    io.xion.infrastructure.network.BridgeResolver bridges;

    @BeforeEach
    void setUp() throws Exception {
        fakeSandboxExecutor.clear();
        fakeResourceGovernor.clear();
        // Clear leftover rows from prior JVM runs sharing the same SQLite file
        store.listAll().forEach(c -> store.delete(c.id()));
        if (!daemonService.isRunning()) {
            daemonService.start();
        }
    }

    @Test
    void createStartListStopViaMediator() {
        String name = "echo-" + UUID.randomUUID().toString().substring(0, 8);
        CreateContainerResult created = mediator.send(new CreateContainerCommand(
                name,
                "sleep",
                List.of("2"),
                List.of(),
                List.of(),
                Optional.empty(),
                ResourceLimits.of("64m", 1.0)));
        assertThat(created.status()).isEqualTo("CREATED");

        StartContainerResult started = mediator.send(new StartContainerCommand(created.id()));
        assertThat(started.status()).isEqualTo("RUNNING");
        assertThat(started.pid()).isPositive();
        // FakeSandboxExecutor is only selected on non-macOS; Darwin path is used on Apple Silicon.
        if (!fakeSandboxExecutor.spawned().isEmpty()) {
            assertThat(fakeResourceGovernor.lastApplied()).isNotNull();
            assertThat(fakeResourceGovernor.lastApplied().memoryBytes()).isPresent();
        }

        ListContainersResult listed = mediator.send(new ListContainersQuery());
        assertThat(listed.containers()).anyMatch(c -> c.id().equals(created.id()) && c.status().name().equals("RUNNING"));

        StopContainerResult stopped = mediator.send(new StopContainerCommand(created.id()));
        assertThat(stopped.status()).isEqualTo("STOPPED");
    }

    @Test
    void ipcCreateAndPs() throws Exception {
        String name = "ipc-" + UUID.randomUUID().toString().substring(0, 8);
        var create = client.object();
        create.put("name", name);
        create.put("binary", "sleep");
        create.putArray("args").add("1");
        var created = client.send("create", create);
        assertThat(created.ok())
                .as("create error: %s", created.error())
                .isTrue();
        assertThat(created.payload().get("id").asText()).isNotBlank();

        var ps = client.send("ps", client.object().put("all", true));
        assertThat(ps.ok()).isTrue();
        assertThat(ps.payload().path("containers").toString()).contains(name);
    }

    @Test
    void networkLsAndUpdateResources() {
        String net = "net-" + UUID.randomUUID().toString().substring(0, 8);
        mediator.send(new CreateNetworkCommand(net));
        ListNetworksResult nets = mediator.send(new ListNetworksQuery());
        assertThat(nets.networks()).anyMatch(n -> n.name().equals(net));

        String name = "upd-" + UUID.randomUUID().toString().substring(0, 8);
        CreateContainerResult created = mediator.send(new CreateContainerCommand(
                name, "sleep", List.of("1"), List.of(), List.of(),
                Optional.empty(), ResourceLimits.unlimited()));
        UpdateContainerResult updated = mediator.send(new UpdateContainerCommand(
                created.id(),
                Optional.of("128m"),
                Optional.of(1.0),
                Optional.of(net),
                false));
        assertThat(updated.message()).contains("memory");
        assertThat(store.findById(created.id()).orElseThrow().network()).contains(net);

        ListContainersResult all = mediator.send(new ListContainersQuery(true));
        assertThat(all.containers()).anyMatch(c -> c.name().equals(name));
        ListContainersResult running = mediator.send(new ListContainersQuery(false));
        assertThat(running.containers()).noneMatch(c -> c.name().equals(name));
    }

    @Test
    void networkCreateWithSubnetShowsGatewayAndMemberIps() throws Exception {
        String net = "subnet-" + UUID.randomUUID().toString().substring(0, 8);
        String subnet = "10.89.42.0/24";
        CreateNetworkResult createdNet = mediator.send(new CreateNetworkCommand(net, subnet));
        assertThat(createdNet.subnet()).isEqualTo(subnet);
        assertThat(createdNet.gateway()).isEqualTo("10.89.42.1");

        ListNetworksResult listed = mediator.send(new ListNetworksQuery());
        assertThat(listed.networks()).anySatisfy(n -> {
            assertThat(n.name()).isEqualTo(net);
            assertThat(n.subnet()).isEqualTo(subnet);
            assertThat(n.gateway()).isEqualTo("10.89.42.1");
        });

        int host1 = freePort();
        int host2 = freePort();
        String app1 = "a1-" + UUID.randomUUID().toString().substring(0, 6);
        String app2 = "a2-" + UUID.randomUUID().toString().substring(0, 6);

        CreateContainerResult c1 = mediator.send(new CreateContainerCommand(
                app1, "sleep", List.of("3"), List.of(),
                List.of(new io.xion.domain.PortMapping(host1, 8087, "tcp")),
                Optional.of(net), ResourceLimits.unlimited()));
        CreateContainerResult c2 = mediator.send(new CreateContainerCommand(
                app2, "sleep", List.of("3"), List.of(),
                List.of(new io.xion.domain.PortMapping(host2, 8087, "tcp")),
                Optional.of(net), ResourceLimits.unlimited()));

        StartContainerResult s1 = mediator.send(new StartContainerCommand(c1.id()));
        StartContainerResult s2 = mediator.send(new StartContainerCommand(c2.id()));
        assertThat(s1.status()).isEqualTo("RUNNING");
        assertThat(s2.status()).isEqualTo("RUNNING");

        assertThat(bridges.ipOf(app1)).contains("10.89.42.2");
        assertThat(bridges.ipOf(app2)).contains("10.89.42.3");
        assertThat(bridges.endpointOf(app1)).contains("10.89.42.2:8087");
        assertThat(bridges.endpointOf(app2)).contains("10.89.42.3:8087");

        InspectNetworkResult inspected = mediator.send(new InspectNetworkQuery(net));
        assertThat(inspected.subnet()).isEqualTo(subnet);
        assertThat(inspected.gateway()).isEqualTo("10.89.42.1");
        assertThat(inspected.memberIps()).containsEntry(app1, "10.89.42.2").containsEntry(app2, "10.89.42.3");
        assertThat(inspected.endpoints()).containsEntry(app1, "10.89.42.2:8087");

        // profile JSON should record allocated IP
        var rec1 = store.findById(c1.id()).orElseThrow();
        assertThat(mapper.readTree(rec1.profileJson()).path("ip").asText()).isEqualTo("10.89.42.2");

        mediator.send(new StopContainerCommand(c1.id()));
        assertThat(bridges.ipOf(app1)).isEmpty();
        assertThat(bridges.endpointOf(app1)).isEmpty();
        // app2 still holds .3; released .2 can be reused later
        assertThat(bridges.ipOf(app2)).contains("10.89.42.3");

        mediator.send(new StopContainerCommand(c2.id()));
        assertThat(bridges.ipOf(app2)).isEmpty();
    }

    @Test
    void removeForceViaMediatorCleansNetworkIp() throws Exception {
        String net = "rmnet-" + UUID.randomUUID().toString().substring(0, 8);
        mediator.send(new CreateNetworkCommand(net, "10.89.55.0/24"));
        int host = freePort();
        String name = "rmapp-" + UUID.randomUUID().toString().substring(0, 6);
        CreateContainerResult created = mediator.send(new CreateContainerCommand(
                name, "sleep", List.of("5"), List.of(),
                List.of(new io.xion.domain.PortMapping(host, 8087, "tcp")),
                Optional.of(net), ResourceLimits.unlimited()));
        mediator.send(new StartContainerCommand(created.id()));
        assertThat(bridges.ipOf(name)).contains("10.89.55.2");

        RemoveContainerResult removed = mediator.send(new RemoveContainerCommand(created.id(), true));
        assertThat(removed.removed()).isTrue();
        assertThat(bridges.ipOf(name)).isEmpty();
        assertThat(bridges.endpointOf(name)).isEmpty();
        assertThat(store.findById(created.id())).isEmpty();
    }

    private static int freePort() throws Exception {
        try (java.net.ServerSocket probe = new java.net.ServerSocket(0)) {
            return probe.getLocalPort();
        }
    }
}
