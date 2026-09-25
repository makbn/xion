package io.xion.application.handlers;

import io.xion.application.mediator.Request;

public record StopContainerCommand(String idOrName) implements Request<StopContainerResult> {
}
