package io.xion.application.handlers;

import io.xion.application.mediator.RequestHandler;
import io.xion.infrastructure.network.BridgeResolver;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class CreateNetworkHandler implements RequestHandler<CreateNetworkCommand, CreateNetworkResult> {

    private final BridgeResolver bridges;

    @Inject
    public CreateNetworkHandler(BridgeResolver bridges) {
        this.bridges = bridges;
    }

    @Override
    public Class<CreateNetworkCommand> requestType() {
        return CreateNetworkCommand.class;
    }

    @Override
    public CreateNetworkResult handle(CreateNetworkCommand request) {
        if (request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("network name required");
        }
        bridges.createNetwork(request.name());
        return new CreateNetworkResult(request.name());
    }
}
