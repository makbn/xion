package io.xion.infrastructure.network;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Daemon-owned network namespace manager: creates bridge subnets, allocates per-app IPs,
 * and applies loopback aliases so reverse-proxy publish can target distinct IP:port pairs.
 */
@ApplicationScoped
public class NetworkNamespaceService {

    private static final Logger LOG = Logger.getLogger(NetworkNamespaceService.class);
    private static final String DEFAULT_SUPERNET_PREFIX = "10.89.";

    private final LoopbackAliasManager aliases;
    private final Map<String, NetworkNamespace> namespaces = new ConcurrentHashMap<>();
    private final AtomicInteger nextSubnetIndex = new AtomicInteger(0);
    private final Set<String> usedCidrs = ConcurrentHashMap.newKeySet();

    @Inject
    public NetworkNamespaceService(LoopbackAliasManager aliases) {
        this.aliases = aliases;
    }

    public NetworkNamespace createNetwork(String name) {
        return createNetwork(name, null);
    }

    public NetworkNamespace createNetwork(String name, String cidrOrNull) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("network name must not be blank");
        }
        return namespaces.compute(name, (n, existing) -> {
            if (existing != null) {
                if (cidrOrNull != null && !existing.cidr().equals(Ipv4Cidr.parse(cidrOrNull).cidr())) {
                    throw new IllegalArgumentException(
                            "Network '" + name + "' already exists with subnet " + existing.cidr()
                                    + "; requested " + cidrOrNull);
                }
                return existing;
            }
            Ipv4Cidr cidr = cidrOrNull == null || cidrOrNull.isBlank()
                    ? allocateDefaultSubnet()
                    : Ipv4Cidr.parse(cidrOrNull);
            if (!usedCidrs.add(cidr.cidr())) {
                throw new IllegalArgumentException("Subnet already in use: " + cidr.cidr());
            }
            LOG.infof("Created network namespace %s subnet %s gateway %s", name, cidr.cidr(), cidr.gateway());
            return new NetworkNamespace(name, cidr);
        });
    }

    public Optional<NetworkNamespace> get(String name) {
        return Optional.ofNullable(namespaces.get(name));
    }

    public List<NetworkNamespace> list() {
        return namespaces.values().stream()
                .sorted((a, b) -> a.name().compareToIgnoreCase(b.name()))
                .toList();
    }

    /**
     * Allocate an IP for {@code containerName} on {@code network}, apply lo0 alias, return the IP.
     */
    public String allocateAndAlias(String network, String containerName) throws IOException {
        NetworkNamespace ns = createNetwork(network);
        String ip = ns.allocateIp(containerName);
        try {
            aliases.addAlias(ip, ns.subnet().netmaskDotted());
        } catch (IOException e) {
            ns.releaseIp(containerName);
            throw e;
        }
        return ip;
    }

    /**
     * Release IP and remove lo0 alias for a container (best-effort alias removal).
     */
    public Optional<String> releaseAndUnalias(String containerName) {
        for (NetworkNamespace ns : namespaces.values()) {
            Optional<String> ip = ns.ipOf(containerName);
            if (ip.isPresent()) {
                ns.releaseIp(containerName);
                try {
                    aliases.removeAlias(ip.get());
                } catch (IOException e) {
                    LOG.warnf(e, "Failed to remove lo0 alias %s for %s", ip.get(), containerName);
                }
                return ip;
            }
        }
        return Optional.empty();
    }

    public void removeNetwork(String name, boolean force) {
        NetworkNamespace ns = namespaces.get(name);
        if (ns == null) {
            throw new IllegalArgumentException("Network not found: " + name);
        }
        if (!ns.members().isEmpty() && !force) {
            throw new IllegalStateException(
                    "Network '" + name + "' has members " + ns.members()
                            + "; disconnect them or pass --force");
        }
        for (String member : Set.copyOf(ns.members())) {
            releaseAndUnalias(member);
        }
        namespaces.remove(name);
        usedCidrs.remove(ns.cidr());
    }

    public Optional<String> ipOf(String containerName) {
        for (NetworkNamespace ns : namespaces.values()) {
            Optional<String> ip = ns.ipOf(containerName);
            if (ip.isPresent()) {
                return ip;
            }
        }
        return Optional.empty();
    }

    private Ipv4Cidr allocateDefaultSubnet() {
        for (int attempt = 0; attempt < 256; attempt++) {
            int idx = nextSubnetIndex.getAndIncrement() & 0xff;
            String candidate = DEFAULT_SUPERNET_PREFIX + idx + ".0/24";
            if (!usedCidrs.contains(candidate)) {
                return Ipv4Cidr.parse(candidate);
            }
        }
        throw new IllegalStateException("Exhausted default 10.89.N.0/24 subnet space");
    }

    /** Visible for tests. */
    List<String> usedCidrs() {
        return new ArrayList<>(usedCidrs);
    }
}
