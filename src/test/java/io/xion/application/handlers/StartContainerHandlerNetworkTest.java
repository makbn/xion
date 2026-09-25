package io.xion.application.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.xion.domain.ContainerRecord;
import io.xion.domain.ContainerStatus;
import io.xion.infrastructure.network.BridgeResolver;
import io.xion.infrastructure.network.NetworkNamespaceService;
import io.xion.infrastructure.network.NoOpLoopbackAliasManager;
import io.xion.infrastructure.network.PortProxy;
import io.xion.infrastructure.process.ProcessRegistry;
import io.xion.infrastructure.process.RuntimePaths;
import io.xion.infrastructure.resources.FakeResourceGovernor;
import io.xion.infrastructure.seatbelt.FakeSandboxExecutor;
import io.xion.infrastructure.seatbelt.SeatbeltProfileGenerator;
import io.xion.infrastructure.store.ContainerStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.ServerSocket;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StartContainerHandlerNetworkTest {

    @TempDir
    Path temp;

    @Test
    void assignsIpInjectsEnvAndProxiesToContainerIp() throws Exception {
        int hostPort = freePort();

        ContainerStore store = mock(ContainerStore.class);
        FakeSandboxExecutor executor = new FakeSandboxExecutor();
        FakeResourceGovernor governor = new FakeResourceGovernor();
        RuntimePaths paths = new RuntimePaths(temp.resolve("rt").toString(), temp.resolve("profiles").toString());
        SeatbeltProfileGenerator gen = new SeatbeltProfileGenerator(temp.resolve("profiles").toString());
        ObjectMapper mapper = new ObjectMapper();
        BridgeResolver bridges = new BridgeResolver(new NetworkNamespaceService(new NoOpLoopbackAliasManager()));
        bridges.createNetwork("frontend", "10.89.0.0/24");
        ProcessRegistry registry = new ProcessRegistry();

        when(store.findById("cid-net")).thenReturn(Optional.of(
                record(mapper, paths, "cid-net", "app1", hostPort, "frontend")));

        StartContainerHandler handler = new StartContainerHandler(
                store, paths, gen, executor, governor, bridges, registry, mapper);

        StartContainerResult result = handler.handle(new StartContainerCommand("cid-net"));
        assertThat(result.status()).isEqualTo("RUNNING");

        Map<String, String> env = executor.lastEnv();
        assertThat(env).containsEntry("XION_IP", "10.89.0.2");
        assertThat(env).containsEntry("XION_NETWORK", "frontend");
        assertThat(env).containsEntry("PORT", "8087");

        assertThat(bridges.endpointOf("app1")).contains("10.89.0.2:8087");
        assertThat(bridges.ipOf("app1")).contains("10.89.0.2");

        Optional<PortProxy> proxy = registry.getProxy("cid-net");
        assertThat(proxy).isPresent();
        assertThat(proxy.get().targetHost()).isEqualTo("10.89.0.2");
        assertThat(proxy.get().mappings()).containsEntry(hostPort, 8087);

        verify(store).update(any(ContainerRecord.class));
        cleanup(registry, bridges, "cid-net", "app1");
    }

    @Test
    void twoContainersSameContainerPortGetDistinctIps() throws Exception {
        int host1 = freePort();
        int host2 = freePort();

        BridgeResolver bridges = new BridgeResolver(new NetworkNamespaceService(new NoOpLoopbackAliasManager()));
        bridges.createNetwork("frontend", "10.89.7.0/24");

        ObjectMapper mapper = new ObjectMapper();
        FakeSandboxExecutor exec = new FakeSandboxExecutor();
        ProcessRegistry registry = new ProcessRegistry();
        RuntimePaths paths = new RuntimePaths(temp.resolve("rt").toString(), temp.resolve("profiles").toString());
        SeatbeltProfileGenerator gen = new SeatbeltProfileGenerator(temp.resolve("profiles").toString());
        FakeResourceGovernor gov = new FakeResourceGovernor();

        ContainerStore store = mock(ContainerStore.class);
        when(store.findById("c1")).thenReturn(Optional.of(record(mapper, paths, "c1", "app1", host1, "frontend")));
        when(store.findById("c2")).thenReturn(Optional.of(record(mapper, paths, "c2", "app2", host2, "frontend")));

        StartContainerHandler handler = new StartContainerHandler(
                store, paths, gen, exec, gov, bridges, registry, mapper);

        handler.handle(new StartContainerCommand("c1"));
        handler.handle(new StartContainerCommand("c2"));

        assertThat(bridges.ipOf("app1")).contains("10.89.7.2");
        assertThat(bridges.ipOf("app2")).contains("10.89.7.3");
        assertThat(bridges.endpointOf("app1")).contains("10.89.7.2:8087");
        assertThat(bridges.endpointOf("app2")).contains("10.89.7.3:8087");

        assertThat(registry.getProxy("c1")).isPresent();
        assertThat(registry.getProxy("c1").get().targetHost()).isEqualTo("10.89.7.2");
        assertThat(registry.getProxy("c2").get().targetHost()).isEqualTo("10.89.7.3");

        assertThat(exec.spawnedEnvs()).hasSize(2);
        assertThat(exec.spawnedEnvs().get(0)).containsEntry("XION_IP", "10.89.7.2");
        assertThat(exec.spawnedEnvs().get(1)).containsEntry("XION_IP", "10.89.7.3");

        cleanup(registry, bridges, "c1", "app1");
        cleanup(registry, bridges, "c2", "app2");
    }

    private static int freePort() throws Exception {
        try (ServerSocket probe = new ServerSocket(0)) {
            return probe.getLocalPort();
        }
    }

    private static ContainerRecord record(
            ObjectMapper mapper, RuntimePaths paths, String id, String name, int hostPort, String network) {
        ObjectNode profile = mapper.createObjectNode();
        profile.put("binary", "sleep");
        profile.putArray("args").add("2");
        profile.putArray("volumes");
        ObjectNode port = profile.putArray("ports").addObject();
        port.put("hostPort", hostPort);
        port.put("containerPort", 8087);
        port.put("protocol", "tcp");
        profile.put("network", network);
        profile.putObject("limits");
        return new ContainerRecord(
                id, name, "sleep", ContainerStatus.CREATED,
                paths.containerDir(id).toString(),
                Optional.empty(), Optional.of(network), Instant.now(),
                Optional.empty(), Optional.empty(), profile.toString());
    }

    private static void cleanup(ProcessRegistry registry, BridgeResolver bridges, String id, String name) {
        registry.removeProxy(id).ifPresent(p -> {
            try {
                p.stop();
            } catch (Exception ignored) {
            }
        });
        registry.remove(id).ifPresent(Process::destroyForcibly);
        bridges.detach(name);
    }
}
