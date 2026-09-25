package io.xion.infrastructure.seatbelt;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Validates a generated {@code .sb} with {@code sandbox-exec} before marking a container RUNNING.
 */
public final class SeatbeltProfileValidator {

    private SeatbeltProfileValidator() {
    }

    /**
     * Runs {@code sandbox-exec -f profile /usr/bin/true}. Throws if the profile is rejected
     * (including Seatbelt "unbound variable" for invalid filter names).
     */
    public static void validateOrThrow(Path profileFile) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(
                "sandbox-exec",
                "-f",
                profileFile.toAbsolutePath().toString(),
                "/usr/bin/true");
        pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        Process p = pb.start();
        String err = new String(p.getErrorStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        boolean finished = p.waitFor(10, TimeUnit.SECONDS);
        if (!finished) {
            p.destroyForcibly();
            throw new IllegalStateException("sandbox-exec timed out validating profile: " + profileFile);
        }
        int code = p.exitValue();
        if (code != 0 || err.toLowerCase().contains("unbound")) {
            throw new IllegalStateException(
                    "Invalid Seatbelt profile (sandbox-exec exit " + code + "): " + profileFile
                            + (err.isEmpty() ? "" : " — " + err));
        }
    }
}
