package io.xion.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResourceLimitsTest {

    @ParameterizedTest
    @CsvSource({
            "512, 512",
            "1k, 1024",
            "1K, 1024",
            "2m, 2097152",
            "512m, 536870912",
            "1g, 1073741824",
            "1.5g, 1610612736"
    })
    void parsesMemory(String input, long expected) {
        assertThat(ResourceLimits.parseMemory(input)).isEqualTo(expected);
    }

    @Test
    void rejectsInvalidMemory() {
        assertThatThrownBy(() -> ResourceLimits.parseMemory("nope"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ResourceLimits.of("0m", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ResourceLimits.of(null, -1.0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ofBuildsOptionalLimits() {
        ResourceLimits limits = ResourceLimits.of("256m", 2.0);
        assertThat(limits.memoryBytes()).contains(256L * 1024 * 1024);
        assertThat(limits.cpus()).contains(2.0);
        assertThat(limits.isLimited()).isTrue();
        assertThat(ResourceLimits.unlimited().isLimited()).isFalse();
    }
}
