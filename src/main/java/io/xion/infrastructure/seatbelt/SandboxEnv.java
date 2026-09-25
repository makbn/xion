package io.xion.infrastructure.seatbelt;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Builds the environment for sandboxed children.
 * <p>
 * Does not inherit the full daemon / host environ (avoids poisoning {@code TMPDIR}
 * with Darwin {@code /var/folders/…} paths that Seatbelt cannot write).
 */
public final class SandboxEnv {

    static final Set<String> KEEP_FROM_HOST = Set.of(
            "PATH", "HOME", "USER", "LOGNAME", "LANG", "LC_ALL", "LC_CTYPE", "TERM", "TZ", "SHELL");

    private SandboxEnv() {
    }

    /**
     * Curated env: selected host keys + forced temp dirs + caller overrides.
     */
    public static Map<String, String> curated(Map<String, String> hostEnv, Map<String, String> overrides) {
        Map<String, String> out = new LinkedHashMap<>();
        if (hostEnv != null) {
            for (String key : KEEP_FROM_HOST) {
                String v = hostEnv.get(key);
                if (v != null && !v.isBlank()) {
                    out.put(key, v);
                }
            }
        }
        // Always remapped — Darwin os.tmpdir() / Node must not see /var/folders under Seatbelt.
        out.put("TMPDIR", "/tmp");
        out.put("TMP", "/tmp");
        out.put("TEMP", "/tmp");
        if (overrides != null) {
            out.putAll(overrides);
            // Re-assert after overrides so profile/host cannot reintroduce /var/folders.
            out.put("TMPDIR", "/tmp");
            out.put("TMP", "/tmp");
            out.put("TEMP", "/tmp");
        }
        return out;
    }
}
