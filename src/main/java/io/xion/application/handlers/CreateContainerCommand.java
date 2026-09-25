package io.xion.application.handlers;

import io.xion.application.mediator.Request;
import io.xion.domain.PortMapping;
import io.xion.domain.ResourceLimits;
import io.xion.domain.VolumeMount;

import java.util.List;
import java.util.Optional;

public record CreateContainerCommand(
        String name,
        String binary,
        List<String> args,
        List<VolumeMount> volumes,
        List<PortMapping> ports,
        Optional<String> network,
        ResourceLimits limits
) implements Request<CreateContainerResult> {
}
