package io.xion.application.handlers;

import io.xion.application.mediator.Request;

public record RemoveContainerCommand(String idOrName, boolean force) implements Request<RemoveContainerResult> {
}
