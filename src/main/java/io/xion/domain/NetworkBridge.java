package io.xion.domain;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Named bridge network snapshot: members, subnet, gateway, and member IPs.
 */
public final class NetworkBridge {

    private final String name;
    private final String cidr;
    private final String gateway;
    private final Set<String> members;
    private final Map<String, String> memberIps;

    public NetworkBridge(
            String name,
            String cidr,
            String gateway,
            Set<String> members,
            Map<String, String> memberIps) {
        this.name = Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("network name must not be blank");
        }
        this.cidr = cidr;
        this.gateway = gateway;
        this.members = Set.copyOf(members == null ? Set.of() : members);
        this.memberIps = Map.copyOf(memberIps == null ? Map.of() : memberIps);
    }

    /** Backward-compatible constructor used by older call sites / tests. */
    public NetworkBridge(String name) {
        this(name, null, null, Set.of(), Map.of());
    }

    public String name() {
        return name;
    }

    public Optional<String> cidr() {
        return Optional.ofNullable(cidr);
    }

    public Optional<String> gateway() {
        return Optional.ofNullable(gateway);
    }

    public boolean contains(String containerName) {
        return members.contains(containerName);
    }

    public Set<String> members() {
        return members;
    }

    public Map<String, String> memberIps() {
        return memberIps;
    }
}
