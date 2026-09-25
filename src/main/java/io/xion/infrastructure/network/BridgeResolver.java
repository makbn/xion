package io.xion.infrastructure.network;

import io.xion.domain.NetworkBridge;
import jakarta.enterprise.context.ApplicationScoped;

import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bridge network registry + hostname resolver for {@code http://container-b} style names.
 */
@ApplicationScoped
public class BridgeResolver {

    private final Map<String, NetworkBridge> bridges = new ConcurrentHashMap<>();
    /** container name → loopback endpoint host:port (or unix path marker) */
    private final Map<String, String> endpoints = new ConcurrentHashMap<>();
    private final Map<String, String> containerNetwork = new ConcurrentHashMap<>();

    public NetworkBridge createNetwork(String name) {
        return bridges.computeIfAbsent(name, NetworkBridge::new);
    }

    public Optional<NetworkBridge> getNetwork(String name) {
        return Optional.ofNullable(bridges.get(name));
    }

    public void attach(String network, String containerName, String endpoint) {
        NetworkBridge bridge = createNetwork(network);
        bridge.attach(containerName);
        endpoints.put(containerName, endpoint);
        containerNetwork.put(containerName, network);
    }

    public void detach(String containerName) {
        String network = containerNetwork.remove(containerName);
        endpoints.remove(containerName);
        if (network != null) {
            NetworkBridge bridge = bridges.get(network);
            if (bridge != null) {
                bridge.detach(containerName);
            }
        }
    }

    /**
     * Resolve a hostname or URL to a registered container endpoint on the same bridge.
     */
    public Optional<String> resolve(String network, String hostOrUrl) {
        String host = extractHost(hostOrUrl);
        NetworkBridge bridge = bridges.get(network);
        if (bridge == null || !bridge.contains(host)) {
            return Optional.empty();
        }
        return Optional.ofNullable(endpoints.get(host));
    }

    public Optional<String> resolveFromContainer(String fromContainer, String hostOrUrl) {
        String network = containerNetwork.get(fromContainer);
        if (network == null) {
            return Optional.empty();
        }
        return resolve(network, hostOrUrl);
    }

    static String extractHost(String hostOrUrl) {
        String raw = hostOrUrl.trim();
        if (raw.contains("://")) {
            URI uri = URI.create(raw);
            if (uri.getHost() != null) {
                return uri.getHost().toLowerCase(Locale.ROOT);
            }
        }
        int slash = raw.indexOf('/');
        if (slash > 0) {
            raw = raw.substring(0, slash);
        }
        int colon = raw.indexOf(':');
        if (colon > 0 && raw.indexOf(']') < 0) {
            raw = raw.substring(0, colon);
        }
        return raw.toLowerCase(Locale.ROOT);
    }

    public Map<String, NetworkBridge> bridges() {
        return Map.copyOf(bridges);
    }
}
