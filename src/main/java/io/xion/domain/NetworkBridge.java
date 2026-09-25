package io.xion.domain;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Named bridge network: registry of container names/ids attached to the network.
 */
public final class NetworkBridge {

    private final String name;
    private final Set<String> members = ConcurrentHashMap.newKeySet();

    public NetworkBridge(String name) {
        this.name = Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("network name must not be blank");
        }
    }

    public String name() {
        return name;
    }

    public void attach(String containerName) {
        members.add(Objects.requireNonNull(containerName));
    }

    public void detach(String containerName) {
        members.remove(containerName);
    }

    public boolean contains(String containerName) {
        return members.contains(containerName);
    }

    public Set<String> members() {
        return Set.copyOf(members);
    }
}
