package io.xion.application.handlers;

import io.xion.domain.ContainerRecord;
import io.xion.infrastructure.network.PortProxy;

import java.util.Optional;

public record InspectContainerResult(
        ContainerRecord container,
        Optional<PortProxy.Stats> proxyStats) {

    public InspectContainerResult(ContainerRecord container) {
        this(container, Optional.empty());
    }
}
