package io.xion.application.handlers;

import io.xion.application.mediator.RequestHandler;
import io.xion.domain.ContainerRecord;
import io.xion.infrastructure.network.PortProxy;
import io.xion.infrastructure.process.ProcessRegistry;
import io.xion.infrastructure.store.ContainerStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Optional;

@ApplicationScoped
public class InspectContainerHandler implements RequestHandler<InspectContainerQuery, InspectContainerResult> {

    private final ContainerStore store;
    private final ProcessRegistry processRegistry;

    @Inject
    public InspectContainerHandler(ContainerStore store, ProcessRegistry processRegistry) {
        this.store = store;
        this.processRegistry = processRegistry;
    }

    /** Test constructor without proxy registry. */
    public InspectContainerHandler(ContainerStore store) {
        this.store = store;
        this.processRegistry = null;
    }

    @Override
    public Class<InspectContainerQuery> requestType() {
        return InspectContainerQuery.class;
    }

    @Override
    public InspectContainerResult handle(InspectContainerQuery request) {
        ContainerRecord record = store.findById(request.idOrName())
                .or(() -> store.findByName(request.idOrName()))
                .orElseThrow(() -> new IllegalArgumentException("Container not found: " + request.idOrName()));
        Optional<PortProxy.Stats> stats = Optional.empty();
        if (processRegistry != null) {
            stats = processRegistry.getProxy(record.id()).map(PortProxy::stats);
        }
        return new InspectContainerResult(record, stats);
    }
}
