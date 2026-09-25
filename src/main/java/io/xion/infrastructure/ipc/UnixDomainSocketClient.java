package io.xion.infrastructure.ipc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.util.UUID;

@ApplicationScoped
public class UnixDomainSocketClient {

    private final Path socketPath;
    private final IpcCodec codec;

    @Inject
    public UnixDomainSocketClient(
            ObjectMapper mapper,
            @ConfigProperty(name = "xion.socket-path") String socketPath) {
        this.codec = new IpcCodec(mapper);
        this.socketPath = Path.of(socketPath);
    }

    public IpcEnvelope send(String type, JsonNode payload) throws IOException {
        return send(type, UUID.randomUUID().toString(), payload);
    }

    public IpcEnvelope send(String type, String requestId, JsonNode payload) throws IOException {
        try (SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX)) {
            channel.connect(UnixDomainSocketAddress.of(socketPath));
            var out = Channels.newOutputStream(channel);
            var in = Channels.newInputStream(channel);
            codec.write(out, IpcEnvelope.command(type, requestId, payload));
            return codec.read(in);
        }
    }

    public Path socketPath() {
        return socketPath;
    }

    public IpcCodec codec() {
        return codec;
    }
}
