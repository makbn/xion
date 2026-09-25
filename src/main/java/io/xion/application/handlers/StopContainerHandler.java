package io.xion.application.handlers;

import io.xion.application.mediator.RequestHandler;
import io.xion.domain.ContainerRecord;
import io.xion.domain.ContainerStatus;
import io.xion.infrastructure.network.BridgeResolver;
import io.xion.infrastructure.process.ProcessRegistry;
import io.xion.infrastructure.store.ContainerStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class StopContainerHandler implements RequestHandler<StopContainerCommand, StopContainerResult> {

    private final ContainerStore store;
    private final ProcessRegistry processRegistry;
    private final BridgeResolver bridges;

    @Inject
    public StopContainerHandler(ContainerStore store, ProcessRegistry processRegistry, BridgeResolver bridges) {
        this.store = store;
        this.processRegistry = processRegistry;
        this.bridges = bridges;
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

        processRegistry.get(record.id()).ifPresent(process -> {
            process.destroy();
            try {
                process.waitFor(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        });
        processRegistry.remove(record.id());
        processRegistry.removeProxy(record.id()).ifPresent(proxy -> {
            try {
                proxy.stop();
            } catch (Exception ignored) {
                // best-effort
            }
        });
        bridges.detach(record.name());

        ContainerRecord updated = record
                .withStatus(ContainerStatus.STOPPED)
                .withPid(null)
                .withStoppedAt(Instant.now());
        store.update(updated);
        return new StopContainerResult(record.id(), record.name(), ContainerStatus.STOPPED.name());
    }
}
