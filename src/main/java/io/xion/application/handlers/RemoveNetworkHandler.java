package io.xion.application.handlers;

import io.xion.application.mediator.RequestHandler;
import io.xion.infrastructure.network.BridgeResolver;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class RemoveNetworkHandler implements RequestHandler<RemoveNetworkCommand, RemoveNetworkResult> {

    private final BridgeResolver bridges;

    @Inject
    public RemoveNetworkHandler(BridgeResolver bridges) {
        this.bridges = bridges;
    }

    @Override
    public Class<RemoveNetworkCommand> requestType() {
        return RemoveNetworkCommand.class;
    }

    @Override
    public RemoveNetworkResult handle(RemoveNetworkCommand request) {
        if (request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("network name required");
        }
        bridges.removeNetwork(request.name(), request.force());
        return new RemoveNetworkResult(request.name(), true);
    }
}
