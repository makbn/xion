package io.xion.domain;

import java.util.Objects;

public final class VolumeMount {

    private final String hostPath;
    private final String containerPath;
    private final boolean readOnly;

    public VolumeMount(String hostPath, String containerPath, boolean readOnly) {
        this.hostPath = Objects.requireNonNull(hostPath, "hostPath");
        this.containerPath = Objects.requireNonNull(containerPath, "containerPath");
        this.readOnly = readOnly;
    }

    public static VolumeMount parse(String spec) {
        Objects.requireNonNull(spec, "spec");
        String[] parts = spec.split(":", 3);
        if (parts.length < 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new IllegalArgumentException("Invalid volume spec (expected host:container[:ro]): " + spec);
        }
        boolean ro = parts.length == 3 && "ro".equalsIgnoreCase(parts[2]);
        return new VolumeMount(parts[0], parts[1], ro);
    }

    public String hostPath() {
        return hostPath;
    }

    public String containerPath() {
        return containerPath;
    }

    public boolean readOnly() {
        return readOnly;
    }
}
