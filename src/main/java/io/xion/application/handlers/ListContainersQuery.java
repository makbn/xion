package io.xion.application.handlers;

import io.xion.application.mediator.Request;

/**
 * @param all when false (default), only RUNNING containers; when true, every status.
 */
public record ListContainersQuery(boolean all) implements Request<ListContainersResult> {

    public ListContainersQuery() {
        this(false);
    }
}
