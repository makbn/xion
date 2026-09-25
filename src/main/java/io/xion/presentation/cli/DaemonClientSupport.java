package io.xion.presentation.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.xion.infrastructure.ipc.IpcEnvelope;
import io.xion.infrastructure.ipc.UnixDomainSocketClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.nio.file.Path;

@ApplicationScoped
public class DaemonClientSupport {

    private final UnixDomainSocketClient client;
    private final ObjectMapper mapper;

    @Inject
    public DaemonClientSupport(UnixDomainSocketClient client, ObjectMapper mapper) {
        this.client = client;
        this.mapper = mapper;
    }

    public IpcEnvelope send(String type, ObjectNode payload) throws Exception {
        return client.send(type, payload);
    }

    public ObjectMapper mapper() {
        return mapper;
    }

    public Path socketPath() {
        return client.socketPath();
    }

    public ObjectNode object() {
        return mapper.createObjectNode();
    }
}
