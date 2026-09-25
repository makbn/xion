package io.xion.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RestartPolicyTest {

    @Test
    void parseKnownPolicies() {
        assertThat(RestartPolicy.parse(null)).isEqualTo(RestartPolicy.NO);
        assertThat(RestartPolicy.parse("")).isEqualTo(RestartPolicy.NO);
        assertThat(RestartPolicy.parse("no").mode()).isEqualTo(RestartPolicy.Mode.NO);
        assertThat(RestartPolicy.parse("always").mode()).isEqualTo(RestartPolicy.Mode.ALWAYS);
        assertThat(RestartPolicy.parse("unless-stopped").mode()).isEqualTo(RestartPolicy.Mode.UNLESS_STOPPED);
        assertThat(RestartPolicy.parse("on-failure").mode()).isEqualTo(RestartPolicy.Mode.ON_FAILURE);
        assertThat(RestartPolicy.parse("on-failure").maxRetries()).isEmpty();
        assertThat(RestartPolicy.parse("on-failure:3").maxRetries()).contains(3);
        assertThat(RestartPolicy.parse("ON-FAILURE:2").wire()).isEqualTo("on-failure:2");
    }

    @Test
    void parseRejectsInvalid() {
        assertThatThrownBy(() -> RestartPolicy.parse("sometimes"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RestartPolicy.parse("on-failure:-1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRestartSemantics() {
        assertThat(RestartPolicy.NO.shouldRestart(1, 1, false)).isFalse();

        RestartPolicy always = RestartPolicy.parse("always");
        assertThat(always.shouldRestart(0, 0, false)).isTrue();
        assertThat(always.shouldRestart(1, 5, false)).isTrue();
        assertThat(always.shouldRestart(1, 5, true)).isFalse();

        RestartPolicy unless = RestartPolicy.parse("unless-stopped");
        assertThat(unless.shouldRestart(0, 0, false)).isTrue();
        assertThat(unless.shouldRestart(0, 0, true)).isFalse();

        RestartPolicy onFail = RestartPolicy.parse("on-failure");
        assertThat(onFail.shouldRestart(0, 0, false)).isFalse();
        assertThat(onFail.shouldRestart(1, 1, false)).isTrue();
        assertThat(onFail.shouldRestart(1, 1, true)).isFalse();

        RestartPolicy capped = RestartPolicy.parse("on-failure:2");
        assertThat(capped.shouldRestart(1, 1, false)).isTrue();
        assertThat(capped.shouldRestart(1, 2, false)).isTrue();
        assertThat(capped.shouldRestart(1, 3, false)).isFalse();
    }
}
