package io.xion.infrastructure.ipc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Length-prefixed JSON wire envelope for UDS IPC.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IpcEnvelope(
        String type,
        String requestId,
        JsonNode payload,
        boolean ok,
        String error
) {
    public static IpcEnvelope command(String type, String requestId, JsonNode payload) {
        return new IpcEnvelope(type, requestId, payload, true, null);
    }

    public static IpcEnvelope success(String type, String requestId, JsonNode payload) {
        return new IpcEnvelope(type, requestId, payload, true, null);
    }

    public static IpcEnvelope failure(String type, String requestId, String error) {
        return new IpcEnvelope(type, requestId, null, false, error);
    }
}
