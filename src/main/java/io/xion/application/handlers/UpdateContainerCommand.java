package io.xion.application.handlers;

import io.xion.application.mediator.Request;

import java.util.Optional;

/**
 * Update stored resource limits and/or bridge network for a container.
 * Resource changes apply on next start; network reconnects immediately if running.
 */
public record UpdateContainerCommand(
        String idOrName,
        Optional<String> memory,
        Optional<Double> cpus,
        Optional<String> network,
        boolean disconnectNetwork
) implements Request<UpdateContainerResult> {
}
