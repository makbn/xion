package io.xion.application.handlers;

import io.xion.application.mediator.RequestHandler;
import io.xion.infrastructure.store.ContainerStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ListContainersHandler implements RequestHandler<ListContainersQuery, ListContainersResult> {

    private final ContainerStore store;

    @Inject
    public ListContainersHandler(ContainerStore store) {
        this.store = store;
    }

    @Override
    public Class<ListContainersQuery> requestType() {
        return ListContainersQuery.class;
    }

    @Override
    public ListContainersResult handle(ListContainersQuery request) {
        return new ListContainersResult(store.listAll());
    }
}
