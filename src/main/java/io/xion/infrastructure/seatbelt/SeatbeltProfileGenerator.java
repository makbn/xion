package io.xion.infrastructure.seatbelt;

import io.xion.domain.ContainerProfile;
import io.xion.domain.PortMapping;
import io.xion.domain.SandboxProfile;
import io.xion.domain.VolumeMount;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Generates macOS Seatbelt ({@code .sb}) profiles.
 * <p>
 * Darwin rejects some filter forms that look plausible in docs:
 * {@code file-read-write} is invalid (use separate {@code file-read*} / {@code file-write*});
 * {@code (local ip "127.0.0.1:…")} / dotted IPv4 hosts are rejected — prefer {@code localhost}
 * and {@code *:port}. Over-narrow {@code file-read*} often causes {@code sandbox-exec} exit 134.
 * <p>
 * Node {@code spawn(…, {stdio:['ignore',…]})} opens {@code /dev/null}; without write allow that
 * fails with EPERM. Mach-O toolchains also need {@code file-map-executable}.
 */
@ApplicationScoped
public class SeatbeltProfileGenerator {

    private static final Logger LOG = Logger.getLogger(SeatbeltProfileGenerator.class);

    private final Path profilesDir;
    /** Daemon-wide extras from config (empty by default — never app-specific). */
    private final List<String> configWritablePaths;

    @Inject
    public SeatbeltProfileGenerator(
            @ConfigProperty(name = "xion.profiles-dir", defaultValue = "/tmp/xion-profiles") String profilesDir,
            @ConfigProperty(name = "xion.sandbox.extra-writable-paths")
                    java.util.Optional<String> extraWritablePaths) {
        this.profilesDir = Path.of(profilesDir);
        this.configWritablePaths = parseCsvPaths(extraWritablePaths.orElse(""));
    }

    /** Manual / unit-test constructor (CDI uses the {@link Inject}-annotated ctor). */
    public SeatbeltProfileGenerator(String profilesDir) {
        this.profilesDir = Path.of(profilesDir);
        this.configWritablePaths = List.of();
    }

    /** Unit-test constructor with daemon-wide writable-path CSV. */
    public SeatbeltProfileGenerator(String profilesDir, String extraWritablePathsCsv) {
        this.profilesDir = Path.of(profilesDir);
        this.configWritablePaths = parseCsvPaths(extraWritablePathsCsv);
    }

    public String generate(ContainerProfile profile, int proxyPort) {
        return generate(profile, proxyPort, null);
    }

    public String generate(ContainerProfile profile, int proxyPort, String assignedIp) {
        SandboxProfile mode = profile.sandboxProfile() == null
                ? SandboxProfile.STRICT
                : profile.sandboxProfile();
        return mode == SandboxProfile.RELAY
                ? generateRelay(profile, proxyPort)
                : generateStrict(profile, proxyPort);
    }

    private String generateStrict(ContainerProfile profile, int proxyPort) {
        List<String> lines = new ArrayList<>();
        lines.add("(version 1)");
        lines.add("(deny default)");
        lines.add("(allow process-exec)");
        lines.add("(allow process-fork)");
        lines.add("(allow signal)");
        lines.add("(allow sysctl-read)");
        lines.add("(allow mach-lookup)");
        // Broad reads: narrow file-read* subpaths abort sandbox-exec with exit 134 on modern Darwin.
        lines.add("(allow file-read*)");
        appendCommonDarwinAllows(lines);

        lines.add("; runtime directory (writes)");
        lines.add(allowSubpath("file-write*", profile.runtimeDir()));

        if (profile.workdir().isPresent()) {
            lines.add("; workdir");
            lines.add(allowSubpath("file-write*", profile.workdir().get()));
        }

        for (VolumeMount volume : profile.volumes()) {
            if (!volume.readOnly()) {
                lines.add("; volume " + volume.hostPath() + " -> " + volume.containerPath());
                lines.add(allowSubpath("file-write*", volume.hostPath()));
            }
        }

        appendTmpWrites(lines);
        appendWritablePaths(lines, profile);
        appendProxyNetwork(lines, profile, proxyPort);
        return String.join("\n", lines) + "\n";
    }

    private String generateRelay(ContainerProfile profile, int proxyPort) {
        List<String> lines = new ArrayList<>();
        lines.add("(version 1)");
        lines.add("(deny default)");
        lines.add("; relay / trusted local app — broad read + outbound; narrow write");
        lines.add("(allow process*)");
        lines.add("(allow process-exec)");
        lines.add("(allow process-fork)");
        lines.add("(allow signal)");
        lines.add("(allow sysctl*)");
        lines.add("(allow mach*)");
        lines.add("(allow file-read*)");
        appendCommonDarwinAllows(lines);

        lines.add("; runtime directory");
        lines.add(allowSubpath("file-write*", profile.runtimeDir()));

        if (profile.workdir().isPresent()) {
            lines.add("; workdir");
            lines.add(allowSubpath("file-write*", profile.workdir().get()));
        }

        for (VolumeMount volume : profile.volumes()) {
            if (!volume.readOnly()) {
                lines.add("; volume " + volume.hostPath() + " -> " + volume.containerPath());
                lines.add(allowSubpath("file-write*", volume.hostPath()));
            }
        }

        appendTmpWrites(lines);
        appendWritablePaths(lines, profile);

        lines.add("(deny network*)");
        lines.add("(allow network-outbound)");
        lines.add("(allow network-inbound (local ip \"localhost:*\"))");
        Set<Integer> published = publishedContainerPorts(profile, proxyPort);
        for (int port : published) {
            lines.add("(allow network-inbound (local ip \"*:" + port + "\"))");
        }

        return String.join("\n", lines) + "\n";
    }

    /**
     * Rules every real macOS / Node app needs (stdio ignore → /dev/null, Mach-O map, sockets).
     */
    private static void appendCommonDarwinAllows(List<String> lines) {
        lines.add("; darwin / node defaults — stdio ignore opens /dev/null");
        lines.add("(allow file-map-executable)");
        lines.add("(allow system-socket)");
        lines.add(allowLiteral("file-write*", "/dev/null"));
        lines.add(allowLiteral("file-read*", "/dev/null"));
        lines.add(allowLiteral("file-write*", "/dev/tty"));
        lines.add(allowLiteral("file-read*", "/dev/tty"));
    }

    private static void appendTmpWrites(List<String> lines) {
        lines.add("; tmp scratch (TMPDIR remapped to /tmp on spawn)");
        lines.add(allowSubpath("file-write*", "/tmp"));
        lines.add(allowSubpath("file-write*", "/private/tmp"));
    }

    private void appendWritablePaths(List<String> lines, ContainerProfile profile) {
        List<String> paths = mergedWritablePaths(profile.writablePaths());
        if (paths.isEmpty()) {
            return;
        }
        lines.add("; extra writable absolute paths (--writable-path / config)");
        for (String dir : paths) {
            lines.add(allowSubpath("file-write*", dir));
        }
    }

    /**
     * Creates absolute writable paths on the host (Docker-style data dirs that apps
     * expect at fixed locations). Paths come from the container profile
     * ({@code --writable-path}) plus optional daemon config
     * {@code xion.sandbox.extra-writable-paths}.
     */
    public List<String> ensureWritablePaths(List<String> profilePaths) {
        List<String> ready = new ArrayList<>();
        for (String dir : mergedWritablePaths(profilePaths)) {
            Path p = Path.of(dir);
            try {
                Files.createDirectories(p);
                ready.add(dir);
            } catch (IOException e) {
                LOG.warnf(
                        "Cannot create writable path '%s' (%s). "
                                + "Mount it with -v HOST:%s or mkdir with sufficient privileges.",
                        dir, e.getMessage(), dir);
            }
        }
        return ready;
    }

    private List<String> mergedWritablePaths(List<String> profilePaths) {
        LinkedHashSet<String> merged = new LinkedHashSet<>(configWritablePaths);
        if (profilePaths != null) {
            merged.addAll(profilePaths);
        }
        return List.copyOf(merged);
    }

    /**
     * Proxy-oriented network: no dotted IPv4 literals (Seatbelt rejects them on current Darwin).
     * {@code assignedIp} is intentionally unused for filter host tokens.
     */
    private static void appendProxyNetwork(List<String> lines, ContainerProfile profile, int proxyPort) {
        lines.add("(deny network*)");
        if (proxyPort > 0) {
            lines.add("(allow network-outbound (remote ip \"localhost:" + proxyPort + "\"))");
            lines.add("(allow network-inbound (local ip \"localhost:*\"))");
            for (int port : publishedContainerPorts(profile, proxyPort)) {
                lines.add("(allow network-inbound (local ip \"*:" + port + "\"))");
            }
        } else if (!profile.ports().isEmpty()) {
            lines.add("(allow network-inbound (local ip \"localhost:*\"))");
            for (PortMapping port : profile.ports()) {
                lines.add("(allow network-outbound (remote ip \"localhost:" + port.containerPort() + "\"))");
                lines.add("(allow network-inbound (local ip \"*:" + port.containerPort() + "\"))");
            }
        }
    }

    private static Set<Integer> publishedContainerPorts(ContainerProfile profile, int proxyPort) {
        Set<Integer> ports = new LinkedHashSet<>();
        for (PortMapping p : profile.ports()) {
            ports.add(p.containerPort());
        }
        if (proxyPort > 0 && profile.ports().isEmpty()) {
            ports.add(proxyPort);
        }
        return ports;
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
        rejectInvalidOp(operation);
        String normalized = Path.of(path).toAbsolutePath().normalize().toString();
        return "(allow " + operation + " (subpath \"" + escape(normalized) + "\"))";
    }

    private static String allowLiteral(String operation, String path) {
        rejectInvalidOp(operation);
        return "(allow " + operation + " (literal \"" + escape(path) + "\"))";
    }

    private static void rejectInvalidOp(String operation) {
        if ("file-read-write".equals(operation)
                || operation.endsWith("-fork*")
                || "process-fork*".equals(operation)) {
            throw new IllegalArgumentException("Invalid Seatbelt filter operation: " + operation);
        }
    }

    private static String escape(String path) {
        return path.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static List<String> parseCsvPaths(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String part : csv.split(",")) {
            String p = part.trim();
            if (!p.isEmpty()) {
                out.add(p);
            }
        }
        return List.copyOf(out);
    }
}
