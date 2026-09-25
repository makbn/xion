package io.xion.infrastructure.process;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks live Process handles for stop/logs.
 */
@ApplicationScoped
public class ProcessRegistry {

    private final Map<String, Process> processes = new ConcurrentHashMap<>();
    private final Map<String, io.xion.infrastructure.network.PortProxy> proxies = new ConcurrentHashMap<>();

    public void register(String containerId, Process process) {
        processes.put(containerId, process);
    }

    public Optional<Process> get(String containerId) {
        return Optional.ofNullable(processes.get(containerId));
    }

    public Optional<Process> remove(String containerId) {
        return Optional.ofNullable(processes.remove(containerId));
    }

    public void registerProxy(String containerId, io.xion.infrastructure.network.PortProxy proxy) {
        proxies.put(containerId, proxy);
    }

    public Optional<io.xion.infrastructure.network.PortProxy> getProxy(String containerId) {
        return Optional.ofNullable(proxies.get(containerId));
    }

    public Optional<io.xion.infrastructure.network.PortProxy> removeProxy(String containerId) {
        return Optional.ofNullable(proxies.remove(containerId));
    }
}
