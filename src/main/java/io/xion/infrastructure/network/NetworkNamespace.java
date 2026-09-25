package io.xion.infrastructure.network;

import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Logical network namespace: named bridge with a private subnet and per-member IPs.
 * Host index 0 = network, 1 = gateway, last = broadcast — not allocated to containers.
 */
public final class NetworkNamespace {

    private final String name;
    private final Ipv4Cidr subnet;
    private final int hostCount;
    private final Set<String> members = ConcurrentHashMap.newKeySet();
    private final Map<String, String> memberIps = new ConcurrentHashMap<>();
    private final Map<String, Integer> memberHostIndex = new ConcurrentHashMap<>();
    private final BitSet allocatedHosts = new BitSet();

    public NetworkNamespace(String name, Ipv4Cidr subnet) {
        this.name = Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("network name must not be blank");
        }
        this.subnet = Objects.requireNonNull(subnet, "subnet");
        this.hostCount = 1 << (32 - subnet.prefixLength());
        if (hostCount < 4) {
            throw new IllegalArgumentException("subnet too small for containers: " + subnet.cidr());
        }
        allocatedHosts.set(0);              // network
        allocatedHosts.set(1);              // gateway
        allocatedHosts.set(hostCount - 1);  // broadcast
    }

    public String name() {
        return name;
    }

    public Ipv4Cidr subnet() {
        return subnet;
    }

    public String cidr() {
        return subnet.cidr();
    }

    public String gateway() {
        return subnet.gateway();
    }

    public synchronized String allocateIp(String containerName) {
        Objects.requireNonNull(containerName, "containerName");
        String existing = memberIps.get(containerName);
        if (existing != null) {
            return existing;
        }
        int next = allocatedHosts.nextClearBit(2);
        if (next < 0 || next >= hostCount - 1) {
            throw new IllegalStateException("No free IPs in network '" + name + "' (" + subnet.cidr() + ")");
        }
        allocatedHosts.set(next);
        String ip = subnet.hostAddress(next);
        members.add(containerName);
        memberIps.put(containerName, ip);
        memberHostIndex.put(containerName, next);
        return ip;
    }

    /**
     * Register membership with a known IP (e.g. legacy attach or reclaim).
     * If {@code ip} is outside the subnet, only the member name is recorded.
     */
    public synchronized void claimIp(String containerName, String ip) {
        Objects.requireNonNull(containerName, "containerName");
        Objects.requireNonNull(ip, "ip");
        String existing = memberIps.get(containerName);
        if (existing != null) {
            return;
        }
        members.add(containerName);
        if (!subnet.contains(ip)) {
            return;
        }
        int hostIndex = Ipv4Cidr.toInt(ip) - Ipv4Cidr.toInt(subnet.networkAddress());
        if (hostIndex <= 1 || hostIndex >= hostCount - 1) {
            throw new IllegalArgumentException("IP " + ip + " is reserved in " + subnet.cidr());
        }
        if (allocatedHosts.get(hostIndex)) {
            throw new IllegalStateException("IP already allocated in network '" + name + "': " + ip);
        }
        allocatedHosts.set(hostIndex);
        memberIps.put(containerName, ip);
        memberHostIndex.put(containerName, hostIndex);
    }

    public void ensureMember(String containerName) {
        members.add(Objects.requireNonNull(containerName));
    }

    public synchronized Optional<String> releaseIp(String containerName) {
        Integer idx = memberHostIndex.remove(containerName);
        String ip = memberIps.remove(containerName);
        members.remove(containerName);
        if (idx != null) {
            allocatedHosts.clear(idx);
        }
        return Optional.ofNullable(ip);
    }

    public Optional<String> ipOf(String containerName) {
        return Optional.ofNullable(memberIps.get(containerName));
    }

    public boolean contains(String containerName) {
        return members.contains(containerName) || memberIps.containsKey(containerName);
    }

    public Set<String> members() {
        return Set.copyOf(members);
    }

    public Map<String, String> memberIps() {
        return Map.copyOf(new LinkedHashMap<>(memberIps));
    }
}
