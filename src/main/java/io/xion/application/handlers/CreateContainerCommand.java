package io.xion.application.handlers;

import io.xion.application.mediator.Request;
import io.xion.domain.PortMapping;
import io.xion.domain.ResourceLimits;
import io.xion.domain.RestartPolicy;
import io.xion.domain.SandboxProfile;
import io.xion.domain.VolumeMount;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public record CreateContainerCommand(
        String name,
        String binary,
        List<String> args,
        List<VolumeMount> volumes,
        List<PortMapping> ports,
        Optional<String> network,
        ResourceLimits limits,
        Map<String, String> env,
        Optional<String> workdir,
        boolean autoRemove,
        RestartPolicy restartPolicy,
        SandboxProfile sandboxProfile,
        List<String> writablePaths
) implements Request<CreateContainerResult> {

    /** Backward-compatible constructor used by older call sites / tests. */
    public CreateContainerCommand(
            String name,
            String binary,
            List<String> args,
            List<VolumeMount> volumes,
            List<PortMapping> ports,
            Optional<String> network,
            ResourceLimits limits) {
        this(name, binary, args, volumes, ports, network, limits,
                Map.of(), Optional.empty(), false, RestartPolicy.NO, SandboxProfile.STRICT, List.of());
    }

    /** Compatibility for call sites without sandbox / writable-path fields. */
    public CreateContainerCommand(
            String name,
            String binary,
            List<String> args,
            List<VolumeMount> volumes,
            List<PortMapping> ports,
            Optional<String> network,
            ResourceLimits limits,
            Map<String, String> env,
            Optional<String> workdir,
            boolean autoRemove,
            RestartPolicy restartPolicy) {
        this(name, binary, args, volumes, ports, network, limits,
                env, workdir, autoRemove, restartPolicy, SandboxProfile.STRICT, List.of());
    }

    public CreateContainerCommand(
            String name,
            String binary,
            List<String> args,
            List<VolumeMount> volumes,
            List<PortMapping> ports,
            Optional<String> network,
            ResourceLimits limits,
            Map<String, String> env,
            Optional<String> workdir,
            boolean autoRemove,
            RestartPolicy restartPolicy,
            SandboxProfile sandboxProfile) {
        this(name, binary, args, volumes, ports, network, limits,
                env, workdir, autoRemove, restartPolicy, sandboxProfile, List.of());
    }
}
