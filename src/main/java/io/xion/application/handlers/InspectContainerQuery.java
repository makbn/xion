package io.xion.application.handlers;

import io.xion.application.mediator.Request;

public record InspectContainerQuery(String idOrName) implements Request<InspectContainerResult> {
}
