package io.xion.application.handlers;

import io.xion.domain.ContainerRecord;

import java.util.List;

public record ListContainersResult(List<ContainerRecord> containers) {
}
