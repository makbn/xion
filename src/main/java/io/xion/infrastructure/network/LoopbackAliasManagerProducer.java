package io.xion.infrastructure.network;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Locale;

@ApplicationScoped
public class LoopbackAliasManagerProducer {

    @ConfigProperty(name = "xion.network.lo0-aliases", defaultValue = "auto")
    String aliasMode;

    @Produces
    @Singleton
    public LoopbackAliasManager loopbackAliasManager(
            DarwinLoopbackAliasManager darwin,
            NoOpLoopbackAliasManager noop) {
        if ("noop".equalsIgnoreCase(aliasMode) || "false".equalsIgnoreCase(aliasMode)) {
            return noop;
        }
        if ("darwin".equalsIgnoreCase(aliasMode) || "true".equalsIgnoreCase(aliasMode)) {
            return darwin;
        }
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("mac")) {
            return darwin;
        }
        return noop;
    }
}
