package io.xion.infrastructure.ipc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class IpcCodecTest {

    private final IpcCodec codec = new IpcCodec(new ObjectMapper());

    @Test
    void roundTripsLengthPrefixedJson() throws Exception {
        ObjectNode payload = new ObjectMapper().createObjectNode().put("hello", "world");
        IpcEnvelope original = IpcEnvelope.command("ping", "req-1", payload);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        codec.write(out, original);

        IpcEnvelope read = codec.read(new ByteArrayInputStream(out.toByteArray()));
        assertThat(read.type()).isEqualTo("ping");
        assertThat(read.requestId()).isEqualTo("req-1");
        assertThat(read.ok()).isTrue();
        assertThat(read.payload().get("hello").asText()).isEqualTo("world");
    }

    @Test
    void encodesFailure() {
        IpcEnvelope fail = IpcEnvelope.failure("start", "r2", "boom");
        assertThat(fail.ok()).isFalse();
        assertThat(fail.error()).isEqualTo("boom");
    }
}
