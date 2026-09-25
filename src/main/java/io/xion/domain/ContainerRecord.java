package io.xion.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public final class ContainerRecord {

    private final String id;
    private final String name;
    private final String binary;
    private final ContainerStatus status;
    private final String runtimeDir;
    private final Optional<Long> pid;
    private final Optional<String> network;
    private final Instant createdAt;
    private final Optional<Instant> startedAt;
    private final Optional<Instant> stoppedAt;
    private final String profileJson;

    public ContainerRecord(
            String id,
            String name,
            String binary,
            ContainerStatus status,
            String runtimeDir,
            Optional<Long> pid,
            Optional<String> network,
            Instant createdAt,
            Optional<Instant> startedAt,
            Optional<Instant> stoppedAt,
            String profileJson) {
        this.id = Objects.requireNonNull(id);
        this.name = Objects.requireNonNull(name);
        this.binary = Objects.requireNonNull(binary);
        this.status = Objects.requireNonNull(status);
        this.runtimeDir = Objects.requireNonNull(runtimeDir);
        this.pid = pid == null ? Optional.empty() : pid;
        this.network = network == null ? Optional.empty() : network;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.startedAt = startedAt == null ? Optional.empty() : startedAt;
        this.stoppedAt = stoppedAt == null ? Optional.empty() : stoppedAt;
        this.profileJson = profileJson == null ? "{}" : profileJson;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String binary() {
        return binary;
    }

    public ContainerStatus status() {
        return status;
    }

    public String runtimeDir() {
        return runtimeDir;
    }

    public Optional<Long> pid() {
        return pid;
    }

    public Optional<String> network() {
        return network;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Optional<Instant> startedAt() {
        return startedAt;
    }

    public Optional<Instant> stoppedAt() {
        return stoppedAt;
    }

    public String profileJson() {
        return profileJson;
    }

    public ContainerRecord withStatus(ContainerStatus newStatus) {
        return new ContainerRecord(id, name, binary, newStatus, runtimeDir, pid, network,
                createdAt, startedAt, stoppedAt, profileJson);
    }

    public ContainerRecord withPid(Long newPid) {
        return new ContainerRecord(id, name, binary, status, runtimeDir, Optional.ofNullable(newPid),
                network, createdAt, startedAt, stoppedAt, profileJson);
    }

    public ContainerRecord withStartedAt(Instant at) {
        return new ContainerRecord(id, name, binary, status, runtimeDir, pid, network,
                createdAt, Optional.ofNullable(at), stoppedAt, profileJson);
    }

    public ContainerRecord withStoppedAt(Instant at) {
        return new ContainerRecord(id, name, binary, status, runtimeDir, pid, network,
                createdAt, startedAt, Optional.ofNullable(at), profileJson);
    }
}
