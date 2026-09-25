package io.xion.infrastructure.process;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Parses Docker-style env files and {@code KEY=VALUE} assignments.
 */
public final class EnvFileParser {

    private EnvFileParser() {
    }

    public static Map<String, String> parse(Path file) {
        Objects.requireNonNull(file, "file");
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("env-file not found: " + file.toAbsolutePath());
        }
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            Map<String, String> out = new LinkedHashMap<>();
            for (String raw : lines) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                if (line.startsWith("export ")) {
                    line = line.substring("export ".length()).trim();
                }
                out.putAll(parseAssignment(line));
            }
            return Map.copyOf(out);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read env-file: " + file + ": " + e.getMessage(), e);
        }
    }

    public static Map<String, String> parseAssignment(String spec) {
        if (spec == null || spec.isBlank()) {
            throw new IllegalArgumentException("Empty env assignment");
        }
        int eq = spec.indexOf('=');
        String key;
        String value;
        if (eq < 0) {
            key = spec.trim();
            value = "";
        } else {
            key = spec.substring(0, eq).trim();
            value = spec.substring(eq + 1);
            if ((value.startsWith("\"") && value.endsWith("\""))
                    || (value.startsWith("'") && value.endsWith("'"))) {
                value = value.substring(1, value.length() - 1);
            }
        }
        if (key.isEmpty()) {
            throw new IllegalArgumentException("Invalid env assignment: " + spec);
        }
        return Map.of(key, value);
    }

    /** Later maps override earlier keys; insertion order preserved. */
    @SafeVarargs
    public static Map<String, String> merge(Map<String, String>... maps) {
        Map<String, String> out = new LinkedHashMap<>();
        if (maps != null) {
            for (Map<String, String> m : maps) {
                if (m != null) {
                    out.putAll(m);
                }
            }
        }
        return Map.copyOf(out);
    }
}
