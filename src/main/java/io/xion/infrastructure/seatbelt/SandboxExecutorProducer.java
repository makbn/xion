package io.xion.infrastructure.seatbelt;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

import java.util.Locale;

@ApplicationScoped
public class SandboxExecutorProducer {

    @Produces
    @Singleton
    public SandboxExecutor sandboxExecutor(FakeSandboxExecutor fake, DarwinSandboxExecutor darwin) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("mac")) {
            return darwin;
        }
        return fake;
    }
}
