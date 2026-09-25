package io.xion.domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable description of a container to create/start.
 */
public final class ContainerProfile {

    private final String id;
    private final String name;
    private final String binary;
    private final List<String> args;
    private final List<VolumeMount> volumes;
    private final List<PortMapping> ports;
    private final Optional<String> network;
    private final ResourceLimits limits;
    private final String runtimeDir;

    public ContainerProfile(
            String id,
            String name,
            String binary,
            List<String> args,
            List<VolumeMount> volumes,
            List<PortMapping> ports,
            Optional<String> network,
            ResourceLimits limits,
            String runtimeDir) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.binary = Objects.requireNonNull(binary, "binary");
        this.args = List.copyOf(args == null ? List.of() : args);
        this.volumes = List.copyOf(volumes == null ? List.of() : volumes);
        this.ports = List.copyOf(ports == null ? List.of() : ports);
        this.network = network == null ? Optional.empty() : network;
        this.limits = limits == null ? ResourceLimits.unlimited() : limits;
        this.runtimeDir = Objects.requireNonNull(runtimeDir, "runtimeDir");
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String binary() {
        return binary;
    }

    public List<String> args() {
        return args;
    }

    public List<VolumeMount> volumes() {
        return volumes;
    }

    public List<PortMapping> ports() {
        return ports;
    }

    public Optional<String> network() {
        return network;
    }

    public ResourceLimits limits() {
        return limits;
    }

    public String runtimeDir() {
        return runtimeDir;
    }

    public ContainerProfile withId(String newId) {
        return new ContainerProfile(newId, name, binary, args, volumes, ports, network, limits, runtimeDir);
    }

    public ContainerProfile withRuntimeDir(String dir) {
        return new ContainerProfile(id, name, binary, args, volumes, ports, network, limits, dir);
    }
}
