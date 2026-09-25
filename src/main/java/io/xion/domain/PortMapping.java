package io.xion.domain;

import java.util.Objects;

public final class PortMapping {

    private final int hostPort;
    private final int containerPort;
    private final String protocol;

    public PortMapping(int hostPort, int containerPort, String protocol) {
        if (hostPort <= 0 || hostPort > 65535) {
            throw new IllegalArgumentException("hostPort out of range: " + hostPort);
        }
        if (containerPort <= 0 || containerPort > 65535) {
            throw new IllegalArgumentException("containerPort out of range: " + containerPort);
        }
        this.hostPort = hostPort;
        this.containerPort = containerPort;
        this.protocol = protocol == null || protocol.isBlank() ? "tcp" : protocol.toLowerCase();
    }

    public static PortMapping parse(String spec) {
        Objects.requireNonNull(spec, "spec");
        // host:container[/proto] or host:container
        String protocol = "tcp";
        String ports = spec;
        int slash = spec.lastIndexOf('/');
        if (slash > 0) {
            protocol = spec.substring(slash + 1);
            ports = spec.substring(0, slash);
        }
        String[] parts = ports.split(":", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid port mapping (expected host:container): " + spec);
        }
        return new PortMapping(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), protocol);
    }

    public int hostPort() {
        return hostPort;
    }

    public int containerPort() {
        return containerPort;
    }

    public String protocol() {
        return protocol;
    }
}
