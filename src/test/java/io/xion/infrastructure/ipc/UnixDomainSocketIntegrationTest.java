package io.xion.infrastructure.ipc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class UnixDomainSocketIntegrationTest {

    @TempDir
    Path temp;

    @Test
    void requestResponseRoundTrip() throws Exception {
        Path sock = temp.resolve("xion.sock");
        ObjectMapper mapper = new ObjectMapper();
        UnixDomainSocketServer server = new UnixDomainSocketServer(mapper, sock.toString());
        AtomicReference<IpcEnvelope> seen = new AtomicReference<>();
        server.setHandler(req -> {
            seen.set(req);
            ObjectNode payload = mapper.createObjectNode().put("pong", true);
            return IpcEnvelope.success("ping", req.requestId(), payload);
        });
        server.start();
        try {
            UnixDomainSocketClient client = new UnixDomainSocketClient(mapper, sock.toString());
            IpcEnvelope response = client.send("ping", mapper.createObjectNode().put("hello", 1));
            assertThat(response.ok()).isTrue();
            assertThat(response.payload().get("pong").asBoolean()).isTrue();
            assertThat(seen.get().type()).isEqualTo("ping");
        } finally {
            server.stop();
        }
    }
}
