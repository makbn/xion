package io.xion.infrastructure.network;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BridgeResolverIpTest {

    @Test
    void attachWithIpGivesDistinctEndpointsForSameContainerPort() throws Exception {
        BridgeResolver resolver = new BridgeResolver(new NetworkNamespaceService(new NoOpLoopbackAliasManager()));
        resolver.createNetwork("frontend", "10.89.0.0/24");

        String ip1 = resolver.attachWithIp("frontend", "app1", 8087);
        String ip2 = resolver.attachWithIp("frontend", "app2", 8087);

        assertThat(ip1).isEqualTo("10.89.0.2");
        assertThat(ip2).isEqualTo("10.89.0.3");
        assertThat(resolver.endpointOf("app1")).contains("10.89.0.2:8087");
        assertThat(resolver.endpointOf("app2")).contains("10.89.0.3:8087");
        assertThat(resolver.resolve("frontend", "app1")).contains("10.89.0.2:8087");
        assertThat(resolver.resolveFromContainer("app2", "http://app1")).contains("10.89.0.2:8087");

        resolver.detach("app1");
        assertThat(resolver.endpointOf("app1")).isEmpty();
        assertThat(resolver.ipOf("app1")).isEmpty();
    }
}
