package io.xion.application.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.xion.application.ContainerTeardown;
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

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Exhaustive coverage for {@link RemoveContainerHandler} cleanup (process, proxy, IP/alias, store).
 */
class RemoveContainerHandlerCleanupTest {

    @TempDir
    Path temp;

    @Test
    void requestTypeIsRemoveCommand() {
        RemoveContainerHandler handler = new RemoveContainerHandler(
                mock(ContainerStore.class), teardown(new ProcessRegistry(), new BridgeResolver()));
        assertThat(handler.requestType()).isEqualTo(RemoveContainerCommand.class);
    }

    @Test
    void notFoundThrows() {
        ContainerStore store = mock(ContainerStore.class);
        when(store.findById("missing")).thenReturn(Optional.empty());
        when(store.findByName("missing")).thenReturn(Optional.empty());

        RemoveContainerHandler remove = new RemoveContainerHandler(
                store, teardown(new ProcessRegistry(), new BridgeResolver()));
        assertThatThrownBy(() -> remove.handle(new RemoveContainerCommand("missing", false)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void resolvesByNameWhenIdMisses() {
        ContainerStore store = mock(ContainerStore.class);
        ContainerRecord stopped = new ContainerRecord(
                "id-1", "by-name", "sleep", ContainerStatus.STOPPED, "/tmp",
                Optional.empty(), Optional.empty(), Instant.now(),
                Optional.empty(), Optional.empty(), "{}");
        when(store.findById("by-name")).thenReturn(Optional.empty());
        when(store.findByName("by-name")).thenReturn(Optional.of(stopped));

        RemoveContainerHandler remove = new RemoveContainerHandler(
                store, teardown(new ProcessRegistry(), new BridgeResolver()));
        RemoveContainerResult result = remove.handle(new RemoveContainerCommand("by-name", false));
        assertThat(result.id()).isEqualTo("id-1");
        assertThat(result.name()).isEqualTo("by-name");
        assertThat(result.removed()).isTrue();
        verify(store).delete("id-1");
    }

    @Test
    void removeRunningWithoutForceRefuses() {
        ContainerStore store = mock(ContainerStore.class);
        ContainerRecord record = new ContainerRecord(
                "x", "x", "sleep", ContainerStatus.RUNNING, "/tmp",
                Optional.of(1L), Optional.empty(), Instant.now(),
                Optional.empty(), Optional.empty(), "{}");
        when(store.findById("x")).thenReturn(Optional.of(record));

        RemoveContainerHandler remove = new RemoveContainerHandler(
                store, teardown(new ProcessRegistry(), new BridgeResolver()));
        assertThatThrownBy(() -> remove.handle(new RemoveContainerCommand("x", false)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RUNNING");
        verify(store, never()).delete("x");
    }

    @Test
    void removeForceCleansProxyIpAndStore() throws Exception {
        int hostPort = freePort();
        ObjectMapper mapper = new ObjectMapper();
        RuntimePaths paths = new RuntimePaths(temp.resolve("rt").toString(), temp.resolve("profiles").toString());
        BridgeResolver bridges = new BridgeResolver(new NetworkNamespaceService(new NoOpLoopbackAliasManager()));
        bridges.createNetwork("frontend", "10.89.9.0/24");
        ProcessRegistry registry = new ProcessRegistry();
        ContainerStore store = mock(ContainerStore.class);

        ContainerRecord record = createdRecord(mapper, paths, "rm1", "web", hostPort);
        when(store.findById("rm1")).thenReturn(Optional.of(record));

        StartContainerHandler start = new StartContainerHandler(
                store, paths,
                new SeatbeltProfileGenerator(temp.resolve("profiles").toString()),
                new FakeSandboxExecutor(), new FakeResourceGovernor(), bridges, registry, mapper);
        start.handle(new StartContainerCommand("rm1"));

        ContainerRecord running = record.withStatus(ContainerStatus.RUNNING).withPid(1L);
        when(store.findById("rm1")).thenReturn(Optional.of(running));

        assertThat(bridges.ipOf("web")).contains("10.89.9.2");
        assertThat(registry.getProxy("rm1")).isPresent();

        RemoveContainerHandler remove = new RemoveContainerHandler(store, teardown(registry, bridges));
        RemoveContainerResult result = remove.handle(new RemoveContainerCommand("rm1", true));
        assertThat(result.removed()).isTrue();

        assertThat(bridges.ipOf("web")).isEmpty();
        assertThat(bridges.endpointOf("web")).isEmpty();
        assertThat(registry.getProxy("rm1")).isEmpty();
        assertThat(registry.get("rm1")).isEmpty();
        verify(store).delete("rm1");
    }

    @Test
    void removeAfterStopStillCleansIdempotently() throws Exception {
        int hostPort = freePort();
        ObjectMapper mapper = new ObjectMapper();
        RuntimePaths paths = new RuntimePaths(temp.resolve("rt").toString(), temp.resolve("profiles").toString());
        BridgeResolver bridges = new BridgeResolver(new NetworkNamespaceService(new NoOpLoopbackAliasManager()));
        bridges.createNetwork("frontend", "10.89.10.0/24");
        ProcessRegistry registry = new ProcessRegistry();
        ContainerStore store = mock(ContainerStore.class);

        ContainerRecord created = createdRecord(mapper, paths, "rm2", "api", hostPort);
        when(store.findById("rm2")).thenReturn(Optional.of(created));

        StartContainerHandler start = new StartContainerHandler(
                store, paths,
                new SeatbeltProfileGenerator(temp.resolve("profiles").toString()),
                new FakeSandboxExecutor(), new FakeResourceGovernor(), bridges, registry, mapper);
        start.handle(new StartContainerCommand("rm2"));

        new StopContainerHandler(store, teardown(registry, bridges)).handle(new StopContainerCommand("rm2"));
        assertThat(bridges.ipOf("api")).isEmpty();

        ContainerRecord stopped = created.withStatus(ContainerStatus.STOPPED).withPid(null);
        when(store.findById("rm2")).thenReturn(Optional.of(stopped));

        RemoveContainerHandler remove = new RemoveContainerHandler(store, teardown(registry, bridges));
        remove.handle(new RemoveContainerCommand("rm2", false));
        assertThat(bridges.ipOf("api")).isEmpty();
        assertThat(registry.getProxy("rm2")).isEmpty();
        verify(store).delete("rm2");
    }

    @Test
    void removeCreatedWithoutRuntimeStillDeletesAndDetaches() {
        ContainerStore store = mock(ContainerStore.class);
        BridgeResolver bridges = new BridgeResolver(new NetworkNamespaceService(new NoOpLoopbackAliasManager()));
        bridges.createNetwork("frontend", "10.89.11.0/24");
        // Member without live process/proxy (create-only)
        bridges.attach("frontend", "idle", "10.89.11.2:0");

        ContainerRecord created = new ContainerRecord(
                "idle-id", "idle", "sleep", ContainerStatus.CREATED, "/tmp",
                Optional.empty(), Optional.of("frontend"), Instant.now(),
                Optional.empty(), Optional.empty(), "{}");
        when(store.findById("idle-id")).thenReturn(Optional.of(created));

        RemoveContainerHandler remove = new RemoveContainerHandler(
                store, teardown(new ProcessRegistry(), bridges));
        remove.handle(new RemoveContainerCommand("idle-id", false));

        assertThat(bridges.endpointOf("idle")).isEmpty();
        assertThat(bridges.ipOf("idle")).isEmpty();
        verify(store).delete("idle-id");
    }

    @Test
    void shutdownDestroysProcessThatStaysAlive() throws Exception {
        ContainerStore store = mock(ContainerStore.class);
        ProcessRegistry registry = new ProcessRegistry();
        Process process = mock(Process.class);
        when(process.waitFor(anyLong(), eq(TimeUnit.SECONDS))).thenReturn(false);
        when(process.isAlive()).thenReturn(true);
        registry.register("alive", process);

        ContainerRecord running = new ContainerRecord(
                "alive", "alive", "sleep", ContainerStatus.RUNNING, "/tmp",
                Optional.of(9L), Optional.empty(), Instant.now(),
                Optional.empty(), Optional.empty(), "{}");
        when(store.findById("alive")).thenReturn(Optional.of(running));

        new RemoveContainerHandler(store, teardown(registry, new BridgeResolver()))
                .handle(new RemoveContainerCommand("alive", true));

        verify(process).destroy();
        verify(process).destroyForcibly();
        assertThat(registry.get("alive")).isEmpty();
        verify(store).delete("alive");
    }

    @Test
    void shutdownHandlesInterruptedWaitFor() throws Exception {
        ContainerStore store = mock(ContainerStore.class);
        ProcessRegistry registry = new ProcessRegistry();
        Process process = mock(Process.class);
        when(process.waitFor(anyLong(), eq(TimeUnit.SECONDS))).thenThrow(new InterruptedException("boom"));
        when(process.isAlive()).thenReturn(false);
        registry.register("intr", process);

        ContainerRecord running = new ContainerRecord(
                "intr", "intr", "sleep", ContainerStatus.RUNNING, "/tmp",
                Optional.of(9L), Optional.empty(), Instant.now(),
                Optional.empty(), Optional.empty(), "{}");
        when(store.findById("intr")).thenReturn(Optional.of(running));

        // Clear any stale interrupt flag from prior tests
        Thread.interrupted();
        new RemoveContainerHandler(store, teardown(registry, new BridgeResolver()))
                .handle(new RemoveContainerCommand("intr", true));

        verify(process).destroy();
        verify(process).destroyForcibly();
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        // Clear interrupt so it does not poison later tests
        Thread.interrupted();
        verify(store).delete("intr");
    }

    @Test
    void proxyStopFailureIsSwallowed() throws Exception {
        ContainerStore store = mock(ContainerStore.class);
        ProcessRegistry registry = new ProcessRegistry();
        PortProxy proxy = mock(PortProxy.class);
        doThrow(new IOException("proxy down")).when(proxy).stop();
        registry.registerProxy("px", proxy);

        ContainerRecord stopped = new ContainerRecord(
                "px", "px", "sleep", ContainerStatus.STOPPED, "/tmp",
                Optional.empty(), Optional.empty(), Instant.now(),
                Optional.empty(), Optional.empty(), "{}");
        when(store.findById("px")).thenReturn(Optional.of(stopped));

        new RemoveContainerHandler(store, teardown(registry, new BridgeResolver()))
                .handle(new RemoveContainerCommand("px", false));

        verify(proxy).stop();
        assertThat(registry.getProxy("px")).isEmpty();
        verify(store).delete("px");
    }

    @Test
    void marksUserStoppedWhenLifecycleWatcherPresent() {
        ContainerStore store = mock(ContainerStore.class);
        ContainerLifecycleWatcher watcher = mock(ContainerLifecycleWatcher.class);
        ContainerRecord stopped = new ContainerRecord(
                "w1", "w1", "sleep", ContainerStatus.STOPPED, "/tmp",
                Optional.empty(), Optional.empty(), Instant.now(),
                Optional.empty(), Optional.empty(), "{}");
        when(store.findById("w1")).thenReturn(Optional.of(stopped));

        new RemoveContainerHandler(store, teardown(new ProcessRegistry(), new BridgeResolver()), watcher)
                .handle(new RemoveContainerCommand("w1", false));

        verify(watcher).markUserStopped("w1");
        verify(store).delete("w1");
    }

    private static ContainerTeardown teardown(ProcessRegistry registry, BridgeResolver bridges) {
        return new ContainerTeardown(registry, bridges);
    }

    private static ContainerRecord createdRecord(
            ObjectMapper mapper, RuntimePaths paths, String id, String name, int hostPort) {
        ObjectNode profile = mapper.createObjectNode();
        profile.put("binary", "sleep");
        profile.putArray("args").add("5");
        profile.putArray("volumes");
        ObjectNode port = profile.putArray("ports").addObject();
        port.put("hostPort", hostPort);
        port.put("containerPort", 8087);
        port.put("protocol", "tcp");
        profile.put("network", "frontend");
        profile.putObject("limits");
        return new ContainerRecord(
                id, name, "sleep", ContainerStatus.CREATED,
                paths.containerDir(id).toString(),
                Optional.empty(), Optional.of("frontend"), Instant.now(),
                Optional.empty(), Optional.empty(), profile.toString());
    }

    private static int freePort() throws Exception {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }
}
