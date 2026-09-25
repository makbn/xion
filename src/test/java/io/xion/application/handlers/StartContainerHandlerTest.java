package io.xion.application.handlers;

import io.xion.domain.ContainerProfile;
import io.xion.domain.PortMapping;
import io.xion.domain.ResourceLimits;
import io.xion.domain.VolumeMount;
import io.xion.infrastructure.process.ProcessRegistry;
import io.xion.infrastructure.process.RuntimePaths;
import io.xion.infrastructure.resources.FakeResourceGovernor;
import io.xion.infrastructure.seatbelt.FakeSandboxExecutor;
import io.xion.infrastructure.seatbelt.SeatbeltProfileGenerator;
import io.xion.infrastructure.network.BridgeResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.xion.domain.ContainerRecord;
import io.xion.domain.ContainerStatus;
import io.xion.infrastructure.store.ContainerStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StartContainerHandlerTest {

    @TempDir
    Path temp;

    @Test
    void appliesGovernorBeforeSpawn() throws Exception {
        ContainerStore store = mock(ContainerStore.class);
        FakeSandboxExecutor executor = new FakeSandboxExecutor();
        FakeResourceGovernor governor = new FakeResourceGovernor();
        RuntimePaths paths = new RuntimePaths(temp.resolve("rt").toString(), temp.resolve("profiles").toString());
        SeatbeltProfileGenerator gen = new SeatbeltProfileGenerator(temp.resolve("profiles").toString());
        ObjectMapper mapper = new ObjectMapper();

        ObjectNode profile = mapper.createObjectNode();
        profile.put("binary", "sleep");
        profile.putArray("args").add("1");
        profile.putArray("volumes");
        profile.putArray("ports");
        profile.putNull("network");
        ObjectNode limits = profile.putObject("limits");
        limits.put("memoryBytes", 64L * 1024 * 1024);
        limits.put("cpus", 1.0);

        ContainerRecord record = new ContainerRecord(
                "cid1", "n1", "sleep", ContainerStatus.CREATED,
                paths.containerDir("cid1").toString(),
                Optional.empty(), Optional.empty(), Instant.now(),
                Optional.empty(), Optional.empty(), profile.toString());
        when(store.findById("cid1")).thenReturn(Optional.of(record));

        StartContainerHandler handler = new StartContainerHandler(
                store, paths, gen, executor, governor, new BridgeResolver(),
                new ProcessRegistry(), mapper);

        StartContainerResult result = handler.handle(new StartContainerCommand("cid1"));
        assertThat(result.status()).isEqualTo("RUNNING");
        assertThat(governor.lastApplied()).isNotNull();
        assertThat(governor.lastApplied().cpus()).contains(1.0);
        assertThat(executor.spawned()).hasSize(1);
        verify(store).update(any(ContainerRecord.class));
    }
}
