package io.xion.application.handlers;

public record StartContainerResult(String id, String name, long pid, String status) {
}
