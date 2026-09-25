package io.xion.application.handlers;

import io.xion.application.ContainerTeardown;
import io.xion.application.mediator.RequestHandler;
import io.xion.domain.ContainerRecord;
import io.xion.domain.ContainerStatus;
import io.xion.infrastructure.store.ContainerStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;

@ApplicationScoped
public class StopContainerHandler implements RequestHandler<StopContainerCommand, StopContainerResult> {

    private final ContainerStore store;
    private final ContainerTeardown teardown;
    private final ContainerLifecycleWatcher lifecycleWatcher;

    @Inject
    public StopContainerHandler(
            ContainerStore store,
            ContainerTeardown teardown,
            ContainerLifecycleWatcher lifecycleWatcher) {
        this.store = store;
        this.teardown = teardown;
        this.lifecycleWatcher = lifecycleWatcher;
    }

    /** Unit-test constructor without a lifecycle watcher. */
    public StopContainerHandler(ContainerStore store, ContainerTeardown teardown) {
        this(store, teardown, null);
    }

    @Override
    public Class<StopContainerCommand> requestType() {
        return StopContainerCommand.class;
    }

    @Override
    public StopContainerResult handle(StopContainerCommand request) {
        ContainerRecord record = store.findById(request.idOrName())
                .or(() -> store.findByName(request.idOrName()))
                .orElseThrow(() -> new IllegalArgumentException("Container not found: " + request.idOrName()));

        if (lifecycleWatcher != null) {
            lifecycleWatcher.markUserStopped(record.id());
        }
        teardown.shutdown(record.id(), record.name(), true);

        ContainerRecord updated = record
                .withStatus(ContainerStatus.STOPPED)
                .withPid(null)
                .withStoppedAt(Instant.now());
        store.update(updated);
        return new StopContainerResult(record.id(), record.name(), ContainerStatus.STOPPED.name());
    }
}
