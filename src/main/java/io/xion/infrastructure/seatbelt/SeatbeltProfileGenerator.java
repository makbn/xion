package io.xion.infrastructure.seatbelt;

import io.xion.domain.ContainerProfile;
import io.xion.domain.PortMapping;
import io.xion.domain.VolumeMount;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates macOS Seatbelt (.sb) profiles: deny-by-default with runtime dir + volume subpaths.
 */
@ApplicationScoped
public class SeatbeltProfileGenerator {

    private final Path profilesDir;

    public SeatbeltProfileGenerator(
            @ConfigProperty(name = "xion.profiles-dir", defaultValue = "/tmp/xion-profiles") String profilesDir) {
        this.profilesDir = Path.of(profilesDir);
    }

    public String generate(ContainerProfile profile, int proxyPort) {
        return generate(profile, proxyPort, null);
    }

    public String generate(ContainerProfile profile, int proxyPort, String assignedIp) {
        List<String> lines = new ArrayList<>();
        lines.add("(version 1)");
        lines.add("(deny default)");
        lines.add("(allow process-exec)");
        lines.add("(allow process-fork)");
        lines.add("(allow signal)");
        lines.add("(allow sysctl-read)");
        lines.add("(allow mach-lookup)");
        lines.add("; runtime directory");
        lines.add(allowSubpath("file-read*", profile.runtimeDir()));
        lines.add(allowSubpath("file-write*", profile.runtimeDir()));
        lines.add(allowSubpath("file-read-write", profile.runtimeDir()));
        if (profile.workdir().isPresent()) {
            String wd = profile.workdir().get();
            lines.add("; workdir");
            lines.add(allowSubpath("file-read-write", wd));
        }
        for (VolumeMount volume : profile.volumes()) {
            String op = volume.readOnly() ? "file-read*" : "file-read-write";
            lines.add("; volume " + volume.hostPath() + " -> " + volume.containerPath());
            lines.add(allowSubpath(op, volume.hostPath()));
        }
        // Network: deny all, then allow loopback / assigned IP for reverse-proxy publish
        lines.add("(deny network*)");
        String bindIp = (assignedIp == null || assignedIp.isBlank()) ? "127.0.0.1" : assignedIp;
        if (proxyPort > 0) {
            lines.add("(allow network-outbound (remote ip \"localhost:" + proxyPort + "\"))");
            lines.add("(allow network-outbound (remote ip \"127.0.0.1:" + proxyPort + "\"))");
            lines.add("(allow network-inbound (local ip \"localhost:*\"))");
            lines.add("(allow network-inbound (local ip \"127.0.0.1:*\"))");
            lines.add("(allow network-inbound (local ip \"" + bindIp + ":*\"))");
            lines.add("(allow network-outbound (remote ip \"" + bindIp + ":*\"))");
        } else if (!profile.ports().isEmpty()) {
            for (PortMapping port : profile.ports()) {
                lines.add("(allow network-outbound (remote ip \"localhost:" + port.containerPort() + "\"))");
                lines.add("(allow network-inbound (local ip \"" + bindIp + ":" + port.containerPort() + "\"))");
                lines.add("(allow network-inbound (local ip \"127.0.0.1:" + port.containerPort() + "\"))");
            }
        } else if (assignedIp != null && !assignedIp.isBlank()) {
            lines.add("(allow network-inbound (local ip \"" + bindIp + ":*\"))");
            lines.add("(allow network-outbound (remote ip \"" + bindIp + ":*\"))");
            lines.add("(allow network-inbound (local ip \"127.0.0.1:*\"))");
        }
        return String.join("\n", lines) + "\n";
    }

    public Path writeProfile(ContainerProfile profile, int proxyPort) throws IOException {
        return writeProfile(profile, proxyPort, null);
    }

    public Path writeProfile(ContainerProfile profile, int proxyPort, String assignedIp) throws IOException {
        Files.createDirectories(profilesDir);
        Path file = profilesDir.resolve(profile.id() + ".sb");
        Files.writeString(file, generate(profile, proxyPort, assignedIp));
        return file;
    }

    private static String allowSubpath(String operation, String path) {
        String normalized = Path.of(path).toAbsolutePath().normalize().toString();
        return "(allow " + operation + " (subpath \"" + escape(normalized) + "\"))";
    }

    private static String escape(String path) {
        return path.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
