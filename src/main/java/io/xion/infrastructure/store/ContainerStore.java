package io.xion.infrastructure.store;

import io.xion.domain.ContainerRecord;
import io.xion.domain.ContainerStatus;

import java.util.List;
import java.util.Optional;

public interface ContainerStore {

    void save(ContainerRecord record);

    Optional<ContainerRecord> findById(String id);

    Optional<ContainerRecord> findByName(String name);

    List<ContainerRecord> listAll();

    List<ContainerRecord> listByStatus(ContainerStatus status);

    void update(ContainerRecord record);

    void delete(String id);
}
