package io.xion.infrastructure.network;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BridgeResolverTest {

    @Test
    void resolvesContainerHostnamesOnSameBridge() {
        BridgeResolver resolver = new BridgeResolver();
        resolver.createNetwork("frontend");
        resolver.attach("frontend", "api", "127.0.0.1:9001");
        resolver.attach("frontend", "web", "127.0.0.1:9002");

        assertThat(resolver.resolve("frontend", "http://api")).contains("127.0.0.1:9001");
        assertThat(resolver.resolve("frontend", "api")).contains("127.0.0.1:9001");
        assertThat(resolver.resolveFromContainer("web", "http://api/health")).contains("127.0.0.1:9001");
        assertThat(resolver.resolve("frontend", "missing")).isEmpty();
        assertThat(resolver.resolve("other", "api")).isEmpty();
    }

    @Test
    void extractHostFromUrls() {
        assertThat(BridgeResolver.extractHost("http://container-b")).isEqualTo("container-b");
        assertThat(BridgeResolver.extractHost("container-b:8080")).isEqualTo("container-b");
        assertThat(BridgeResolver.extractHost("API")).isEqualTo("api");
    }
}
