package io.xion.domain;

import java.util.Locale;

/**
 * Seatbelt policy presets.
 *
 * <ul>
 *   <li>{@link #STRICT} — deny-by-default; volume/runtime writes; proxy-oriented network.</li>
 *   <li>{@link #RELAY} — trusted local apps (media relays, Node + host ffmpeg): broad reads,
 *       full outbound network, narrow writes. Weaker confidentiality isolation.</li>
 * </ul>
 */
public enum SandboxProfile {
    STRICT,
    RELAY;

    public static SandboxProfile parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return STRICT;
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        return switch (s) {
            case "strict", "default" -> STRICT;
            case "relay", "network-relay", "devtools" -> RELAY;
            default -> throw new IllegalArgumentException(
                    "Invalid sandbox profile '" + raw + "' (strict|relay|network-relay|devtools)");
        };
    }

    public String wire() {
        return switch (this) {
            case STRICT -> "strict";
            case RELAY -> "relay";
        };
    }
}
