package io.xion.application.handlers;

import io.xion.application.mediator.Request;

public record CreateNetworkCommand(String name, String subnet) implements Request<CreateNetworkResult> {

    public CreateNetworkCommand(String name) {
        this(name, null);
    }
}
