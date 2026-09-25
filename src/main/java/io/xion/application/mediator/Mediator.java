package io.xion.application.mediator;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.HashMap;
import java.util.Map;

@ApplicationScoped
public class Mediator {

    private final Map<Class<?>, RequestHandler<?, ?>> handlers = new HashMap<>();

    @Inject
    public Mediator(Instance<RequestHandler<?, ?>> handlerInstances) {
        this((Iterable<RequestHandler<?, ?>>) handlerInstances);
    }

    /** Test / programmatic construction. */
    public Mediator(Iterable<RequestHandler<?, ?>> handlerInstances) {
        for (RequestHandler<?, ?> handler : handlerInstances) {
            Class<?> type = handler.requestType();
            if (handlers.containsKey(type)) {
                throw new IllegalStateException("Duplicate handler for " + type.getName());
            }
            handlers.put(type, handler);
        }
    }

    @SuppressWarnings("unchecked")
    public <R> R send(Request<R> request) {
        RequestHandler<Request<R>, R> handler = (RequestHandler<Request<R>, R>) handlers.get(request.getClass());
        if (handler == null) {
            throw new IllegalArgumentException("No handler registered for " + request.getClass().getName());
        }
        return handler.handle(request);
    }

    public boolean hasHandler(Class<? extends Request<?>> type) {
        return handlers.containsKey(type);
    }

    public int handlerCount() {
        return handlers.size();
    }
}
