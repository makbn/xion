package io.xion.application.mediator;

/**
 * Handles a single request type. Discovered via CDI; new features = new handler beans.
 */
public interface RequestHandler<Req extends Request<R>, R> {

    Class<Req> requestType();

    R handle(Req request);
}
