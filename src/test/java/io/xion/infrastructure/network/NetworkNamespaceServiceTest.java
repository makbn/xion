package io.xion.infrastructure.network;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NetworkNamespaceServiceTest {

    static final class RecordingAliases implements LoopbackAliasManager {
        final List<String> added = new ArrayList<>();
        final List<String> removed = new ArrayList<>();

        @Override
        public void addAlias(String ip, String netmask) {
            added.add(ip);
        }

        @Override
        public void removeAlias(String ip) {
            removed.add(ip);
        }
    }

    @Test
    void allocatesDistinctIpsAndAliases() throws Exception {
        RecordingAliases aliases = new RecordingAliases();
        NetworkNamespaceService svc = new NetworkNamespaceService(aliases);
        svc.createNetwork("frontend", "10.89.0.0/24");

        String ip1 = svc.allocateAndAlias("frontend", "app1");
        String ip2 = svc.allocateAndAlias("frontend", "app2");
        assertThat(ip1).isEqualTo("10.89.0.2");
        assertThat(ip2).isEqualTo("10.89.0.3");
        assertThat(aliases.added).containsExactly("10.89.0.2", "10.89.0.3");

        assertThat(svc.releaseAndUnalias("app1")).contains("10.89.0.2");
        assertThat(aliases.removed).containsExactly("10.89.0.2");
    }

    @Test
    void defaultSubnetsAreUnique() {
        NetworkNamespaceService svc = new NetworkNamespaceService(new NoOpLoopbackAliasManager());
        NetworkNamespace a = svc.createNetwork("a");
        NetworkNamespace b = svc.createNetwork("b");
        assertThat(a.cidr()).isNotEqualTo(b.cidr());
        assertThat(a.cidr()).startsWith("10.89.");
    }

    @Test
    void rejectsConflictingSubnetOnExistingName() {
        NetworkNamespaceService svc = new NetworkNamespaceService(new NoOpLoopbackAliasManager());
        svc.createNetwork("frontend", "10.89.0.0/24");
        assertThatThrownBy(() -> svc.createNetwork("frontend", "10.89.1.0/24"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }
}
