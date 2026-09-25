package io.xion.application.handlers;

import io.xion.application.mediator.RequestHandler;
import io.xion.domain.ContainerRecord;
import io.xion.domain.ContainerStatus;
import io.xion.infrastructure.network.BridgeResolver;
import io.xion.infrastructure.process.ProcessRegistry;
import io.xion.infrastructure.store.ContainerStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class RemoveContainerHandler implements RequestHandler<RemoveContainerCommand, RemoveContainerResult> {

    private final ContainerStore store;
    private final ProcessRegistry processRegistry;
    private final BridgeResolver bridges;

    @Inject
    public RemoveContainerHandler(ContainerStore store, ProcessRegistry processRegistry, BridgeResolver bridges) {
        this.store = store;
        this.processRegistry = processRegistry;
        this.bridges = bridges;
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

        if (record.status() == ContainerStatus.RUNNING) {
            if (!request.force()) {
                throw new IllegalStateException(
                        "Container is RUNNING; stop it first or pass --force to stop+remove");
            }
            processRegistry.get(record.id()).ifPresent(process -> {
                process.destroy();
                try {
                    process.waitFor(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    process.destroyForcibly();
                }
            });
            processRegistry.remove(record.id());
            processRegistry.removeProxy(record.id()).ifPresent(proxy -> {
                try {
                    proxy.stop();
                } catch (Exception ignored) {
                }
            });
        }

        bridges.detach(record.name());
        store.delete(record.id());
        return new RemoveContainerResult(record.id(), record.name(), true);
    }
}
