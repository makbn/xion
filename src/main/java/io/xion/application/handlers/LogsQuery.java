package io.xion.application.handlers;

import io.xion.application.mediator.Request;

public record LogsQuery(String idOrName, boolean stderr) implements Request<LogsResult> {
}
