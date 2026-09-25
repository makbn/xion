package io.xion.application.handlers;

import io.xion.application.mediator.Request;

public record InspectNetworkQuery(String name) implements Request<InspectNetworkResult> {
}
