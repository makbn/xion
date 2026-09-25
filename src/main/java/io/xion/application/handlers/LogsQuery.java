package io.xion.application.handlers;

import io.xion.application.mediator.Request;

public record LogsQuery(String idOrName, boolean stderr, int tail, boolean follow) implements Request<LogsResult> {

    /** Snapshot of full log (no tail / follow). */
    public LogsQuery(String idOrName, boolean stderr) {
        this(idOrName, stderr, 0, false);
    }
}
