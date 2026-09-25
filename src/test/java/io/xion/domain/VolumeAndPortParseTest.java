package io.xion.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VolumeAndPortParseTest {

    @Test
    void parsesVolume() {
        VolumeMount rw = VolumeMount.parse("/host/data:/data");
        assertThat(rw.hostPath()).isEqualTo("/host/data");
        assertThat(rw.containerPath()).isEqualTo("/data");
        assertThat(rw.readOnly()).isFalse();

        VolumeMount ro = VolumeMount.parse("/host/cfg:/cfg:ro");
        assertThat(ro.readOnly()).isTrue();
    }

    @Test
    void rejectsBadVolume() {
        assertThatThrownBy(() -> VolumeMount.parse("only-one"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parsesPortMapping() {
        PortMapping tcp = PortMapping.parse("8080:80");
        assertThat(tcp.hostPort()).isEqualTo(8080);
        assertThat(tcp.containerPort()).isEqualTo(80);
        assertThat(tcp.protocol()).isEqualTo("tcp");

        PortMapping udp = PortMapping.parse("53:53/udp");
        assertThat(udp.protocol()).isEqualTo("udp");
    }
}
