package io.xion.application.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import io.xion.application.mediator.DaemonService;
import io.xion.application.mediator.Mediator;
import io.xion.domain.ResourceLimits;
import io.xion.infrastructure.resources.FakeResourceGovernor;
import io.xion.infrastructure.seatbelt.FakeSandboxExecutor;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

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
    ObjectMapper mapper;

    @Inject
    io.xion.presentation.cli.DaemonClientSupport client;

    @BeforeEach
    void setUp() throws Exception {
        fakeSandboxExecutor.clear();
        fakeResourceGovernor.clear();
        if (!daemonService.isRunning()) {
            daemonService.start();
        }
    }

    @Test
    void createStartListStopViaMediator() {
        CreateContainerResult created = mediator.send(new CreateContainerCommand(
                "echo-box",
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
        var create = client.object();
        create.put("name", "ipc-box");
        create.put("binary", "sleep");
        create.putArray("args").add("1");
        var created = client.send("create", create);
        assertThat(created.ok()).isTrue();
        assertThat(created.payload().get("id").asText()).isNotBlank();

        var ps = client.send("ps", client.object());
        assertThat(ps.ok()).isTrue();
        assertThat(ps.payload().path("containers").toString()).contains("ipc-box");
    }
}
