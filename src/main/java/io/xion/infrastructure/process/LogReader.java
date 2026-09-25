package io.xion.infrastructure.process;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Objects;

/**
 * Utilities for reading container log files (snapshot tail and incremental follow).
 */
public final class LogReader {

    private LogReader() {
    }

    /**
     * Return the last {@code n} lines of {@code path}. When {@code n <= 0}, returns the full file.
     */
    public static String tail(Path path, int n) throws IOException {
        Objects.requireNonNull(path, "path");
        if (!Files.exists(path)) {
            return "";
        }
        if (n <= 0) {
            return Files.readString(path);
        }
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        if (lines.isEmpty()) {
            return "";
        }
        int from = Math.max(0, lines.size() - n);
        List<String> slice = lines.subList(from, lines.size());
        String joined = String.join("\n", slice);
        // Preserve trailing newline when the source file had one
        byte[] raw = Files.readAllBytes(path);
        if (raw.length > 0 && raw[raw.length - 1] == '\n') {
            return joined + "\n";
        }
        return joined;
    }

    /**
     * Incremental reader for growing log files (byte-offset based).
     */
    public static final class Follow {
        private final Path path;
        private long offset;

        public Follow(Path path) {
            this.path = Objects.requireNonNull(path, "path");
        }

        public Path path() {
            return path;
        }

        public long offset() {
            return offset;
        }

        /** Read and return any new bytes since the last call. Empty if unchanged. */
        public String readNew() throws IOException {
            if (!Files.exists(path)) {
                return "";
            }
            long size = Files.size(path);
            if (size < offset) {
                offset = 0;
            }
            if (size <= offset) {
                return "";
            }
            try (SeekableByteChannel ch = Files.newByteChannel(path, StandardOpenOption.READ)) {
                ch.position(offset);
                int len = (int) Math.min(size - offset, Integer.MAX_VALUE);
                ByteBuffer buf = ByteBuffer.allocate(len);
                while (buf.hasRemaining()) {
                    int n = ch.read(buf);
                    if (n < 0) {
                        break;
                    }
                }
                offset = size;
                return new String(buf.array(), 0, buf.position(), StandardCharsets.UTF_8);
            }
        }
    }
}
