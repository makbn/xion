package io.xion.infrastructure.network;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

import java.util.Locale;

@ApplicationScoped
public class LoopbackAliasManagerProducer {

    @Produces
    @Singleton
    public LoopbackAliasManager loopbackAliasManager(
            DarwinLoopbackAliasManager darwin,
            NoOpLoopbackAliasManager noop) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("mac")) {
            return darwin;
        }
        return noop;
    }
}
