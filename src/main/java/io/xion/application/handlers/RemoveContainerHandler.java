package io.xion.application.handlers;

import io.xion.application.ContainerTeardown;
import io.xion.application.mediator.RequestHandler;
import io.xion.domain.ContainerRecord;
import io.xion.domain.ContainerStatus;
import io.xion.infrastructure.store.ContainerStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class RemoveContainerHandler implements RequestHandler<RemoveContainerCommand, RemoveContainerResult> {

    private final ContainerStore store;
    private final ContainerTeardown teardown;
    private final ContainerLifecycleWatcher lifecycleWatcher;

    @Inject
    public RemoveContainerHandler(
            ContainerStore store,
            ContainerTeardown teardown,
            ContainerLifecycleWatcher lifecycleWatcher) {
        this.store = store;
        this.teardown = teardown;
        this.lifecycleWatcher = lifecycleWatcher;
    }

    /** Unit-test constructor without a lifecycle watcher. */
    public RemoveContainerHandler(ContainerStore store, ContainerTeardown teardown) {
        this(store, teardown, null);
    }

    @Override
    public Class<RemoveContainerCommand> requestType() {
        return RemoveContainerCommand.class;
    }

    @Override
    public RemoveContainerResult handle(RemoveContainerCommand request) {
        ContainerRecord record = store.findById(request.idOrName())
                .or(() -> store.findByName(request.idOrName()))
                .orElseThrow(() -> new IllegalArgumentException("Container not found: " + request.idOrName()));

        if (record.status() == ContainerStatus.RUNNING && !request.force()) {
            throw new IllegalStateException(
                    "Container is RUNNING; stop it first or pass --force to stop+remove");
        }

        if (lifecycleWatcher != null) {
            lifecycleWatcher.markUserStopped(record.id());
        }
        teardown.shutdown(record.id(), record.name(), true);
        store.delete(record.id());
        return new RemoveContainerResult(record.id(), record.name(), true);
    }
}
