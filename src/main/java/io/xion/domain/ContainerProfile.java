package io.xion.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final Map<String, String> env;
    private final Optional<String> workdir;
    private final boolean autoRemove;
    private final RestartPolicy restartPolicy;

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
        this(id, name, binary, args, volumes, ports, network, limits, runtimeDir,
                Map.of(), Optional.empty(), false, RestartPolicy.NO);
    }

    public ContainerProfile(
            String id,
            String name,
            String binary,
            List<String> args,
            List<VolumeMount> volumes,
            List<PortMapping> ports,
            Optional<String> network,
            ResourceLimits limits,
            String runtimeDir,
            Map<String, String> env,
            Optional<String> workdir,
            boolean autoRemove,
            RestartPolicy restartPolicy) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.binary = Objects.requireNonNull(binary, "binary");
        this.args = List.copyOf(args == null ? List.of() : args);
        this.volumes = List.copyOf(volumes == null ? List.of() : volumes);
        this.ports = List.copyOf(ports == null ? List.of() : ports);
        this.network = network == null ? Optional.empty() : network;
        this.limits = limits == null ? ResourceLimits.unlimited() : limits;
        this.runtimeDir = Objects.requireNonNull(runtimeDir, "runtimeDir");
        this.env = Map.copyOf(env == null ? Map.of() : new LinkedHashMap<>(env));
        this.workdir = workdir == null ? Optional.empty() : workdir;
        this.autoRemove = autoRemove;
        this.restartPolicy = restartPolicy == null ? RestartPolicy.NO : restartPolicy;
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

    public Map<String, String> env() {
        return env;
    }

    public Optional<String> workdir() {
        return workdir;
    }

    public boolean autoRemove() {
        return autoRemove;
    }

    public RestartPolicy restartPolicy() {
        return restartPolicy;
    }

    public ContainerProfile withId(String newId) {
        return new ContainerProfile(newId, name, binary, args, volumes, ports, network, limits, runtimeDir,
                env, workdir, autoRemove, restartPolicy);
    }

    public ContainerProfile withRuntimeDir(String dir) {
        return new ContainerProfile(id, name, binary, args, volumes, ports, network, limits, dir,
                env, workdir, autoRemove, restartPolicy);
    }
}
