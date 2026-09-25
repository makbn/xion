package io.xion.application.handlers;

import io.xion.application.mediator.Request;

public record StartContainerCommand(String idOrName) implements Request<StartContainerResult> {
}
