package io.xion.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SandboxProfileTest {

    @Test
    void parsesAliases() {
        assertThat(SandboxProfile.parse(null)).isEqualTo(SandboxProfile.STRICT);
        assertThat(SandboxProfile.parse("")).isEqualTo(SandboxProfile.STRICT);
        assertThat(SandboxProfile.parse("strict")).isEqualTo(SandboxProfile.STRICT);
        assertThat(SandboxProfile.parse("default")).isEqualTo(SandboxProfile.STRICT);
        assertThat(SandboxProfile.parse("relay")).isEqualTo(SandboxProfile.RELAY);
        assertThat(SandboxProfile.parse("network-relay")).isEqualTo(SandboxProfile.RELAY);
        assertThat(SandboxProfile.parse("devtools")).isEqualTo(SandboxProfile.RELAY);
    }

    @Test
    void rejectsUnknown() {
        assertThatThrownBy(() -> SandboxProfile.parse("loose"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("strict|relay");
    }
}
