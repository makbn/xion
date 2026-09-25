package io.xion.infrastructure.network;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NetworkNamespaceTest {

    @Test
    void allocatesSequentialIpsSkippingGateway() {
        NetworkNamespace ns = new NetworkNamespace("frontend", Ipv4Cidr.parse("10.89.0.0/24"));
        assertThat(ns.gateway()).isEqualTo("10.89.0.1");
        assertThat(ns.allocateIp("app1")).isEqualTo("10.89.0.2");
        assertThat(ns.allocateIp("app2")).isEqualTo("10.89.0.3");
        assertThat(ns.allocateIp("app1")).isEqualTo("10.89.0.2"); // idempotent
        assertThat(ns.memberIps()).containsEntry("app1", "10.89.0.2").containsEntry("app2", "10.89.0.3");
    }

    @Test
    void releaseAllowsReuse() {
        NetworkNamespace ns = new NetworkNamespace("frontend", Ipv4Cidr.parse("10.89.1.0/24"));
        assertThat(ns.allocateIp("a")).isEqualTo("10.89.1.2");
        assertThat(ns.allocateIp("b")).isEqualTo("10.89.1.3");
        assertThat(ns.releaseIp("a")).contains("10.89.1.2");
        assertThat(ns.allocateIp("c")).isEqualTo("10.89.1.2");
    }

    @Test
    void claimIpReservesSpecificAddress() {
        NetworkNamespace ns = new NetworkNamespace("frontend", Ipv4Cidr.parse("10.89.2.0/24"));
        ns.claimIp("api", "10.89.2.10");
        assertThat(ns.ipOf("api")).contains("10.89.2.10");
        assertThatThrownBy(() -> ns.claimIp("other", "10.89.2.10"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(ns.allocateIp("web")).isEqualTo("10.89.2.2");
    }
}
