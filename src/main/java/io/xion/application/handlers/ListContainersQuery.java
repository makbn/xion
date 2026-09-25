package io.xion.application.handlers;

import io.xion.application.mediator.Request;
import io.xion.domain.ContainerRecord;

import java.util.List;

public record ListContainersQuery() implements Request<ListContainersResult> {
}
