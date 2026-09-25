package io.xion.application.handlers;

import io.xion.application.mediator.RequestHandler;
import io.xion.domain.ContainerRecord;
import io.xion.infrastructure.store.ContainerStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class InspectContainerHandler implements RequestHandler<InspectContainerQuery, InspectContainerResult> {

    private final ContainerStore store;

    @Inject
    public InspectContainerHandler(ContainerStore store) {
        this.store = store;
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
        return new InspectContainerResult(record);
    }
}
