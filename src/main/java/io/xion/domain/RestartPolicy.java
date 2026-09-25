package io.xion.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Docker-compatible restart policy.
 */
public record RestartPolicy(Mode mode, Optional<Integer> maxRetries) {

    public static final RestartPolicy NO = new RestartPolicy(Mode.NO, Optional.empty());

    public enum Mode {
        NO,
        ON_FAILURE,
        ALWAYS,
        UNLESS_STOPPED
    }

    public RestartPolicy {
        Objects.requireNonNull(mode, "mode");
        maxRetries = maxRetries == null ? Optional.empty() : maxRetries;
        if (mode != Mode.ON_FAILURE && maxRetries.isPresent()) {
            throw new IllegalArgumentException("maxRetries only valid for on-failure");
        }
    }

    public static RestartPolicy parse(String raw) {
        if (raw == null || raw.isBlank() || "no".equalsIgnoreCase(raw.trim())) {
            return NO;
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if ("always".equals(s)) {
            return new RestartPolicy(Mode.ALWAYS, Optional.empty());
        }
        if ("unless-stopped".equals(s)) {
            return new RestartPolicy(Mode.UNLESS_STOPPED, Optional.empty());
        }
        if ("on-failure".equals(s)) {
            return new RestartPolicy(Mode.ON_FAILURE, Optional.empty());
        }
        if (s.startsWith("on-failure:")) {
            String n = s.substring("on-failure:".length()).trim();
            int max = Integer.parseInt(n);
            if (max < 0) {
                throw new IllegalArgumentException("on-failure max retries must be >= 0: " + raw);
            }
            return new RestartPolicy(Mode.ON_FAILURE, Optional.of(max));
        }
        throw new IllegalArgumentException(
                "Invalid restart policy '" + raw + "' (no|on-failure[:N]|always|unless-stopped)");
    }

    public String wire() {
        return switch (mode) {
            case NO -> "no";
            case ALWAYS -> "always";
            case UNLESS_STOPPED -> "unless-stopped";
            case ON_FAILURE -> maxRetries.map(n -> "on-failure:" + n).orElse("on-failure");
        };
    }

    public boolean shouldRestart(int exitCode, int failureCount, boolean userStopped) {
        return switch (mode) {
            case NO -> false;
            case ALWAYS -> !userStopped;
            case UNLESS_STOPPED -> !userStopped;
            case ON_FAILURE -> {
                if (userStopped || exitCode == 0) {
                    yield false;
                }
                if (maxRetries.isEmpty()) {
                    yield true;
                }
                yield failureCount <= maxRetries.get();
            }
        };
    }
}
