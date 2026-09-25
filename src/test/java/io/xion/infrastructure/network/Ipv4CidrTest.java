package io.xion.infrastructure.network;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Ipv4CidrTest {

    @Test
    void parsesSlash24() {
        Ipv4Cidr cidr = Ipv4Cidr.parse("10.89.0.0/24");
        assertThat(cidr.cidr()).isEqualTo("10.89.0.0/24");
        assertThat(cidr.gateway()).isEqualTo("10.89.0.1");
        assertThat(cidr.broadcast()).isEqualTo("10.89.0.255");
        assertThat(cidr.hostAddress(2)).isEqualTo("10.89.0.2");
        assertThat(cidr.netmaskDotted()).isEqualTo("255.255.255.0");
        assertThat(cidr.contains("10.89.0.50")).isTrue();
        assertThat(cidr.contains("10.89.1.2")).isFalse();
    }

    @Test
    void rejectsBadCidr() {
        assertThatThrownBy(() -> Ipv4Cidr.parse("10.0.0.0"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Ipv4Cidr.parse("10.0.0.0/7"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
