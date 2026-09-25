package io.xion.application.handlers;

public record UpdateContainerResult(
        String id,
        String name,
        String status,
        String message,
        boolean restartRequired
) {
}
