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
        assertThat(fakeSandboxExecutor.spawned()).isNotEmpty();
        assertThat(fakeResourceGovernor.lastApplied()).isNotNull();
        assertThat(fakeResourceGovernor.lastApplied().memoryBytes()).isPresent();

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
}
