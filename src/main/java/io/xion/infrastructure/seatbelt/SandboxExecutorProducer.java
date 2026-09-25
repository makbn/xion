package io.xion.infrastructure.seatbelt;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Locale;

@ApplicationScoped
public class SandboxExecutorProducer {

    @Produces
    @Singleton
    public SandboxExecutor sandboxExecutor(
            FakeSandboxExecutor fake,
            DarwinSandboxExecutor darwin,
            @ConfigProperty(name = "xion.sandbox", defaultValue = "auto") String mode) {
        String m = mode == null ? "auto" : mode.trim().toLowerCase(Locale.ROOT);
        if ("fake".equals(m)) {
            return fake;
        }
        if ("darwin".equals(m)) {
            return darwin;
        }
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("mac")) {
            return darwin;
        }
        return fake;
    }
}
