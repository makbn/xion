package io.xion.application.handlers;

import io.xion.application.mediator.Request;

public record CreateNetworkCommand(String name) implements Request<CreateNetworkResult> {
}
