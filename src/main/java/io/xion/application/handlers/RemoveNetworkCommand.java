package io.xion.application.handlers;

import io.xion.application.mediator.Request;

public record RemoveNetworkCommand(String name, boolean force) implements Request<RemoveNetworkResult> {
}
