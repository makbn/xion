package io.xion.infrastructure.ipc;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Length-prefixed (4-byte big-endian) JSON codec for UDS frames.
 */
public final class IpcCodec {

    private final ObjectMapper mapper;

    public IpcCodec(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public void write(OutputStream out, IpcEnvelope envelope) throws IOException {
        byte[] body = mapper.writeValueAsBytes(envelope);
        byte[] header = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(body.length).array();
        out.write(header);
        out.write(body);
        out.flush();
    }

    public IpcEnvelope read(InputStream in) throws IOException {
        byte[] header = in.readNBytes(4);
        if (header.length < 4) {
            throw new EOFException("Incomplete IPC frame header");
        }
        int len = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN).getInt();
        if (len < 0 || len > 16 * 1024 * 1024) {
            throw new IOException("Invalid IPC frame length: " + len);
        }
        byte[] body = in.readNBytes(len);
        if (body.length < len) {
            throw new EOFException("Incomplete IPC frame body");
        }
        return mapper.readValue(body, IpcEnvelope.class);
    }

    public String toJson(IpcEnvelope envelope) throws IOException {
        return mapper.writeValueAsString(envelope);
    }

    public IpcEnvelope fromJson(String json) throws IOException {
        return mapper.readValue(json.getBytes(StandardCharsets.UTF_8), IpcEnvelope.class);
    }

    public ObjectMapper mapper() {
        return mapper;
    }
}
