package io.xion.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Optional memory and CPU limits passed through create/start (--memory, --cpus).
 */
public final class ResourceLimits {

    private static final Pattern MEMORY = Pattern.compile(
            "^(\\d+(?:\\.\\d+)?)\\s*([kKmMgGtT]?[iI]?[bB]?)?$");

    private final Optional<Long> memoryBytes;
    private final Optional<Double> cpus;

    public ResourceLimits(Optional<Long> memoryBytes, Optional<Double> cpus) {
        this.memoryBytes = memoryBytes == null ? Optional.empty() : memoryBytes;
        this.cpus = cpus == null ? Optional.empty() : cpus;
        memoryBytes.ifPresent(bytes -> {
            if (bytes <= 0) {
                throw new IllegalArgumentException("memoryBytes must be positive");
            }
        });
        cpus.ifPresent(c -> {
            if (c <= 0) {
                throw new IllegalArgumentException("cpus must be positive");
            }
        });
    }

    public static ResourceLimits unlimited() {
        return new ResourceLimits(Optional.empty(), Optional.empty());
    }

    public static ResourceLimits of(String memory, Double cpus) {
        Optional<Long> mem = memory == null || memory.isBlank()
                ? Optional.empty()
                : Optional.of(parseMemory(memory));
        Optional<Double> cpu = cpus == null ? Optional.empty() : Optional.of(cpus);
        return new ResourceLimits(mem, cpu);
    }

    public static long parseMemory(String raw) {
        Objects.requireNonNull(raw, "memory");
        Matcher m = MEMORY.matcher(raw.trim());
        if (!m.matches()) {
            throw new IllegalArgumentException("Invalid memory limit: " + raw);
        }
        double value = Double.parseDouble(m.group(1));
        String unit = m.group(2) == null ? "" : m.group(2).toLowerCase(Locale.ROOT);
        long multiplier = switch (unit) {
            case "", "b" -> 1L;
            case "k", "kb", "ki", "kib" -> 1024L;
            case "m", "mb", "mi", "mib" -> 1024L * 1024L;
            case "g", "gb", "gi", "gib" -> 1024L * 1024L * 1024L;
            case "t", "tb", "ti", "tib" -> 1024L * 1024L * 1024L * 1024L;
            default -> throw new IllegalArgumentException("Unknown memory unit: " + unit);
        };
        long bytes = (long) (value * multiplier);
        if (bytes <= 0) {
            throw new IllegalArgumentException("memory must be positive: " + raw);
        }
        return bytes;
    }

    public Optional<Long> memoryBytes() {
        return memoryBytes;
    }

    public Optional<Double> cpus() {
        return cpus;
    }

    public boolean isLimited() {
        return memoryBytes.isPresent() || cpus.isPresent();
    }
}
