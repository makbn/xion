package io.xion.application;

import io.xion.infrastructure.network.BridgeResolver;
import io.xion.infrastructure.process.ProcessRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.concurrent.TimeUnit;

/**
 * Shared shutdown of live container resources: process, port proxy, and optional network detach.
 */
@ApplicationScoped
public class ContainerTeardown {

    private final ProcessRegistry processRegistry;
    private final BridgeResolver bridges;

    @Inject
    public ContainerTeardown(ProcessRegistry processRegistry, BridgeResolver bridges) {
        this.processRegistry = processRegistry;
        this.bridges = bridges;
    }

    /**
     * Stop the process and proxy for {@code containerId}. When {@code detachNetwork} is true,
     * also release the bridge IP/alias for {@code containerName}.
     */
    public void shutdown(String containerId, String containerName, boolean detachNetwork) {
        processRegistry.get(containerId).ifPresent(process -> {
            process.destroy();
            try {
                process.waitFor(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        });
        processRegistry.remove(containerId);
        processRegistry.removeProxy(containerId).ifPresent(proxy -> {
            try {
                proxy.stop();
            } catch (Exception ignored) {
                // best-effort
            }
        });
        if (detachNetwork && containerName != null) {
            bridges.detach(containerName);
        }
    }
}
