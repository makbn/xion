package io.xion.application.handlers;

import io.xion.application.mediator.RequestHandler;
import io.xion.domain.ContainerRecord;
import io.xion.infrastructure.process.RuntimePaths;
import io.xion.infrastructure.store.ContainerStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.nio.file.Files;

@ApplicationScoped
public class LogsHandler implements RequestHandler<LogsQuery, LogsResult> {

    private final ContainerStore store;
    private final RuntimePaths paths;

    @Inject
    public LogsHandler(ContainerStore store, RuntimePaths paths) {
        this.store = store;
        this.paths = paths;
    }

    @Override
    public Class<LogsQuery> requestType() {
        return LogsQuery.class;
    }

    @Override
    public LogsResult handle(LogsQuery request) {
        ContainerRecord record = store.findById(request.idOrName())
                .or(() -> store.findByName(request.idOrName()))
                .orElseThrow(() -> new IllegalArgumentException("Container not found: " + request.idOrName()));
        var logPath = request.stderr() ? paths.stderrLog(record.id()) : paths.stdoutLog(record.id());
        try {
            if (!Files.exists(logPath)) {
                return new LogsResult(record.id(), "");
            }
            return new LogsResult(record.id(), Files.readString(logPath));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read logs: " + e.getMessage(), e);
        }
    }
}
