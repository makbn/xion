package io.xion.application.mediator;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MediatorTest {

    record Ping(String message) implements Request<String> {
    }

    static final class PingHandler implements RequestHandler<Ping, String> {
        @Override
        public Class<Ping> requestType() {
            return Ping.class;
        }

        @Override
        public String handle(Ping request) {
            return "pong:" + request.message();
        }
    }

    record Unknown() implements Request<Void> {
    }

    @Test
    void routesToRegisteredHandler() {
        Mediator mediator = new Mediator(List.of(new PingHandler()));
        assertThat(mediator.send(new Ping("hi"))).isEqualTo("pong:hi");
        assertThat(mediator.handlerCount()).isEqualTo(1);
        assertThat(mediator.hasHandler(Ping.class)).isTrue();
    }

    @Test
    void rejectsUnknownRequest() {
        Mediator mediator = new Mediator(List.of(new PingHandler()));
        assertThatThrownBy(() -> mediator.send(new Unknown()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No handler");
    }

    @Test
    void rejectsDuplicateHandlers() {
        assertThatThrownBy(() -> new Mediator(List.of(new PingHandler(), new PingHandler())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate");
    }
}
