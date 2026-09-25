package io.xion.infrastructure.store;

import io.quarkus.test.junit.QuarkusTest;
import io.xion.domain.ContainerRecord;
import io.xion.domain.ContainerStatus;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class SqliteContainerStoreTest {

    @Inject
    ContainerStore store;

    @Test
    void persistsAndListsContainers() {
        String id = UUID.randomUUID().toString().substring(0, 12);
        ContainerRecord record = new ContainerRecord(
                id,
                "store-" + id,
                "/bin/true",
                ContainerStatus.CREATED,
                "/tmp/xion/" + id,
                Optional.empty(),
                Optional.of("bridge1"),
                Instant.now(),
                Optional.empty(),
                Optional.empty(),
                "{}");
        store.save(record);
        assertThat(store.findById(id)).isPresent();
        assertThat(store.findByName("store-" + id)).isPresent();
        assertThat(store.listAll()).anyMatch(c -> c.id().equals(id));

        store.update(record.withStatus(ContainerStatus.RUNNING).withPid(42L));
        assertThat(store.findById(id).orElseThrow().status()).isEqualTo(ContainerStatus.RUNNING);
        assertThat(store.findById(id).orElseThrow().pid()).contains(42L);
    }
}
