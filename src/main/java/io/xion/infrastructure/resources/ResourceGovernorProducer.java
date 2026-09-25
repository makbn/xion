package io.xion.infrastructure.resources;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

import java.util.Locale;

@ApplicationScoped
public class ResourceGovernorProducer {

    @Produces
    @Singleton
    public ResourceGovernor resourceGovernor(FakeResourceGovernor fake, DarwinResourceGovernor darwin) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("mac")) {
            return darwin;
        }
        return fake;
    }
}
