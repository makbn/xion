package io.xion.infrastructure.network;

import io.xion.domain.NetworkBridge;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.IOException;
import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bridge network registry + hostname resolver for {@code http://container-b} style names.
 * Delegates subnet/IP allocation to {@link NetworkNamespaceService}.
 */
@ApplicationScoped
public class BridgeResolver {

    private final NetworkNamespaceService namespaces;
    /** container name → loopback endpoint host:port */
    private final Map<String, String> endpoints = new ConcurrentHashMap<>();
    private final Map<String, String> containerNetwork = new ConcurrentHashMap<>();

    @Inject
    public BridgeResolver(NetworkNamespaceService namespaces) {
        this.namespaces = namespaces;
    }

    /** Test helper when CDI is unavailable. */
    public BridgeResolver() {
        this(new NetworkNamespaceService(new NoOpLoopbackAliasManager()));
    }

    public NetworkBridge createNetwork(String name) {
        return toBridge(namespaces.createNetwork(name));
    }

    public NetworkBridge createNetwork(String name, String subnet) {
        return toBridge(namespaces.createNetwork(name, subnet));
    }

    public Optional<NetworkBridge> getNetwork(String name) {
        return namespaces.get(name).map(this::toBridge);
    }

    /**
     * Attach container to network with a pre-built endpoint (IP:port).
     * Prefer {@link #attachWithIp} when starting containers.
     */
    public void attach(String network, String containerName, String endpoint) {
        NetworkNamespace ns = namespaces.createNetwork(network);
        String hostPart = endpoint;
        int colon = endpoint.indexOf(':');
        if (colon > 0) {
            hostPart = endpoint.substring(0, colon);
        }
        if (ns.subnet().contains(hostPart)) {
            ns.claimIp(containerName, hostPart);
        } else {
            ns.ensureMember(containerName);
        }
        endpoints.put(containerName, endpoint);
        containerNetwork.put(containerName, network);
    }

    /**
     * Allocate IP, apply lo0 alias, and register endpoint {@code ip:port}.
     *
     * @return allocated IP
     */
    public String attachWithIp(String network, String containerName, int containerPort) throws IOException {
        String ip = namespaces.allocateAndAlias(network, containerName);
        String endpoint = ip + ":" + Math.max(0, containerPort);
        endpoints.put(containerName, endpoint);
        containerNetwork.put(containerName, network);
        return ip;
    }

    public void detach(String containerName) {
        containerNetwork.remove(containerName);
        endpoints.remove(containerName);
        namespaces.releaseAndUnalias(containerName);
    }

    public Optional<String> networkOf(String containerName) {
        return Optional.ofNullable(containerNetwork.get(containerName));
    }

    public Optional<String> endpointOf(String containerName) {
        return Optional.ofNullable(endpoints.get(containerName));
    }

    public Optional<String> ipOf(String containerName) {
        return namespaces.ipOf(containerName);
    }

    public void removeNetwork(String name, boolean force) {
        NetworkNamespace ns = namespaces.get(name)
                .orElseThrow(() -> new IllegalArgumentException("Network not found: " + name));
        if (!ns.members().isEmpty() && !force) {
            throw new IllegalStateException(
                    "Network '" + name + "' has members " + ns.members()
                            + "; disconnect them or pass --force");
        }
        for (String member : java.util.Set.copyOf(ns.members())) {
            endpoints.remove(member);
            containerNetwork.remove(member);
        }
        // Also clear endpoints that referenced this network without IP alloc bookkeeping
        containerNetwork.entrySet().removeIf(e -> {
            if (name.equals(e.getValue())) {
                endpoints.remove(e.getKey());
                return true;
            }
            return false;
        });
        namespaces.removeNetwork(name, true);
    }

    public java.util.List<NetworkBridge> listNetworks() {
        return namespaces.list().stream().map(this::toBridge).toList();
    }

    public Optional<String> resolve(String network, String hostOrUrl) {
        String host = extractHost(hostOrUrl);
        NetworkNamespace ns = namespaces.get(network).orElse(null);
        if (ns == null || !ns.contains(host)) {
            // Fall back to endpoint registry by name even if IP not in members set
            if (ns != null && endpoints.containsKey(host) && network.equals(containerNetwork.get(host))) {
                return Optional.ofNullable(endpoints.get(host));
            }
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
        Map<String, NetworkBridge> map = new ConcurrentHashMap<>();
        for (NetworkBridge b : listNetworks()) {
            map.put(b.name(), b);
        }
        return Map.copyOf(map);
    }

    private NetworkBridge toBridge(NetworkNamespace ns) {
        return new NetworkBridge(ns.name(), ns.cidr(), ns.gateway(), ns.members(), ns.memberIps());
    }
}
