package io.xion.presentation.cli.compat;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Translates a Docker CLI invocation into an equivalent {@code xion} argv.
 * Unsupported flags are recorded; callers decide whether to allow partial ports/flags.
 */
public final class DockerCommandTranslator {

    public record Translation(
            List<String> xionArgs,
            List<String> dropped,
            List<String> warnings,
            boolean partial,
            String summary
    ) {
        public Translation {
            xionArgs = List.copyOf(xionArgs);
            dropped = List.copyOf(dropped);
            warnings = List.copyOf(warnings);
            Objects.requireNonNull(summary);
        }

        public boolean hasDropped() {
            return !dropped.isEmpty();
        }
    }

    public static final class Options {
        public boolean allowPartial = false;
        public boolean dropUnsupported = false;
        public boolean partialPorts = false;
        public boolean stripImageTag = true;
    }

    public Translation translate(List<String> rawDockerArgs) {
        return translate(rawDockerArgs, new Options());
    }

    public Translation translate(List<String> rawDockerArgs, Options options) {
        Objects.requireNonNull(rawDockerArgs, "rawDockerArgs");
        Objects.requireNonNull(options, "options");
        List<String> args = normalize(rawDockerArgs);
        if (args.isEmpty()) {
            throw new IllegalArgumentException("Empty docker command; expected e.g. run|ps|stop|logs|…");
        }

        String sub = args.getFirst().toLowerCase(Locale.ROOT);
        List<String> rest = args.subList(1, args.size());
        return switch (sub) {
            case "run" -> translateRun(rest, options);
            case "create" -> translateCreate(rest, options);
            case "start" -> translateSimple("start", rest, "CONTAINER", options);
            case "stop" -> translateSimple("stop", rest, "CONTAINER", options);
            case "logs" -> translateLogs(rest, options);
            case "ps", "container", "ls" -> translatePs(sub, rest, options);
            case "network" -> translateNetwork(rest, options);
            case "version" -> new Translation(List.of("version"), List.of(), List.of(), false,
                    "docker version → xion version");
            case "help" -> new Translation(helpArgs(rest), List.of(), List.of(), false,
                    "docker help → xion help");
            default -> throw new IllegalArgumentException(
                    "Unsupported docker subcommand: " + sub
                            + " (supported: run, create, start, stop, logs, ps, network, version, help)");
        };
    }

    private static List<String> helpArgs(List<String> rest) {
        if (rest.isEmpty()) {
            return List.of("--help");
        }
        List<String> out = new ArrayList<>();
        out.add(rest.getFirst());
        out.add("--help");
        return out;
    }

    private Translation translatePs(String sub, List<String> rest, Options options) {
        // docker container ls / docker ps
        if ("container".equals(sub)) {
            if (rest.isEmpty() || !List.of("ls", "ps", "list").contains(rest.getFirst().toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Expected: docker container ls");
            }
            rest = rest.subList(1, rest.size());
        }
        List<String> dropped = new ArrayList<>();
        for (String a : rest) {
            if (a.startsWith("-")) {
                dropped.add(a);
            }
        }
        ensureDroppable(dropped, options);
        return new Translation(
                List.of("ps"),
                dropped,
                dropped.isEmpty() ? List.of() : List.of("Ignored docker ps filters/flags; xion ps lists all containers"),
                !dropped.isEmpty(),
                "docker ps → xion ps");
    }

    private Translation translateSimple(String xionCmd, List<String> rest, String noun, Options options) {
        List<String> dropped = new ArrayList<>();
        String target = null;
        for (int i = 0; i < rest.size(); i++) {
            String a = rest.get(i);
            if (a.startsWith("-")) {
                if (isFlagWithValue(a) && i + 1 < rest.size() && !rest.get(i + 1).startsWith("-")) {
                    dropped.add(a + " " + rest.get(i + 1));
                    i++;
                } else {
                    dropped.add(a);
                }
            } else if (target == null) {
                target = a;
            } else {
                dropped.add(a);
            }
        }
        if (target == null) {
            throw new IllegalArgumentException("docker " + xionCmd + " requires a " + noun);
        }
        ensureDroppable(dropped, options);
        return new Translation(
                List.of(xionCmd, target),
                dropped,
                List.of(),
                !dropped.isEmpty(),
                "docker " + xionCmd + " " + target + " → xion " + xionCmd + " " + target);
    }

    private Translation translateLogs(List<String> rest, Options options) {
        List<String> dropped = new ArrayList<>();
        boolean stderr = false;
        String target = null;
        for (int i = 0; i < rest.size(); i++) {
            String a = rest.get(i);
            if ("--stderr".equals(a)) {
                stderr = true;
            } else if ("-f".equals(a) || "--follow".equals(a) || "--tail".equals(a) || "-t".equals(a)
                    || "--timestamps".equals(a) || "--since".equals(a) || "--until".equals(a)) {
                if (("--tail".equals(a) || "--since".equals(a) || "--until".equals(a))
                        && i + 1 < rest.size() && !rest.get(i + 1).startsWith("-")) {
                    dropped.add(a + " " + rest.get(++i));
                } else {
                    dropped.add(a);
                }
            } else if (a.startsWith("-")) {
                dropped.add(a);
            } else if (target == null) {
                target = a;
            } else {
                dropped.add(a);
            }
        }
        if (target == null) {
            throw new IllegalArgumentException("docker logs requires a CONTAINER");
        }
        ensureDroppable(dropped, options);
        List<String> xion = new ArrayList<>();
        xion.add("logs");
        if (stderr) {
            xion.add("--stderr");
        }
        xion.add(target);
        List<String> warnings = new ArrayList<>();
        if (!dropped.isEmpty()) {
            warnings.add("Follow/tail/timestamps are not supported by xion logs yet");
        }
        return new Translation(xion, dropped, warnings, !dropped.isEmpty(),
                "docker logs → xion " + String.join(" ", xion));
    }

    private Translation translateNetwork(List<String> rest, Options options) {
        if (rest.isEmpty()) {
            throw new IllegalArgumentException("Expected: docker network create NAME");
        }
        String action = rest.getFirst().toLowerCase(Locale.ROOT);
        if (!"create".equals(action)) {
            throw new IllegalArgumentException("Only 'docker network create' is supported (got: network " + action + ")");
        }
        List<String> dropped = new ArrayList<>();
        String name = null;
        for (int i = 1; i < rest.size(); i++) {
            String a = rest.get(i);
            if (a.startsWith("-")) {
                if (isFlagWithValue(a) && i + 1 < rest.size() && !rest.get(i + 1).startsWith("-")) {
                    dropped.add(a + " " + rest.get(++i));
                } else {
                    dropped.add(a);
                }
            } else if (name == null) {
                name = a;
            } else {
                dropped.add(a);
            }
        }
        if (name == null) {
            throw new IllegalArgumentException("docker network create requires a NAME");
        }
        ensureDroppable(dropped, options);
        return new Translation(
                List.of("network", "create", name),
                dropped,
                List.of(),
                !dropped.isEmpty(),
                "docker network create " + name + " → xion network create " + name);
    }

    private Translation translateRun(List<String> rest, Options options) {
        return translateRunOrCreate("run", rest, options);
    }

    private Translation translateCreate(List<String> rest, Options options) {
        return translateRunOrCreate("create", rest, options);
    }

    private Translation translateRunOrCreate(String xionCmd, List<String> rest, Options options) {
        List<String> dropped = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        String name = null;
        String memory = null;
        Double cpus = null;
        String network = null;
        List<String> ports = new ArrayList<>();
        List<String> volumes = new ArrayList<>();
        boolean detachSeen = false;

        int i = 0;
        while (i < rest.size()) {
            String a = rest.get(i);
            if ("--".equals(a)) {
                i++;
                break;
            }
            if (!a.startsWith("-")) {
                break;
            }
            switch (a) {
                case "-d", "--detach" -> {
                    detachSeen = true;
                    i++;
                }
                case "--name" -> {
                    name = requireValue("run", a, rest, ++i);
                    i++;
                }
                case "-n" -> {
                    // docker uses -n rarely; treat as name if next token present
                    name = requireValue("run", a, rest, ++i);
                    i++;
                }
                case "-p", "--publish" -> {
                    String spec = requireValue("run", a, rest, ++i);
                    i++;
                    addPort(spec, ports, dropped, warnings, options);
                }
                case "--publish-all", "-P" -> {
                    dropped.add(a);
                    warnings.add("-P/--publish-all has no Xion equivalent (explicit -p required)");
                    i++;
                }
                case "-v", "--volume" -> {
                    String spec = requireValue("run", a, rest, ++i);
                    i++;
                    volumes.add(spec);
                }
                case "--mount" -> {
                    String mount = requireValue("run", a, rest, ++i);
                    i++;
                    String converted = convertMount(mount);
                    if (converted != null) {
                        volumes.add(converted);
                        warnings.add("Converted --mount to -v " + converted);
                    } else {
                        dropped.add("--mount " + mount);
                        warnings.add("Could not convert --mount; use -v host:container");
                    }
                }
                case "--network", "--net" -> {
                    network = requireValue("run", a, rest, ++i);
                    i++;
                }
                case "-m", "--memory" -> {
                    memory = requireValue("run", a, rest, ++i);
                    i++;
                }
                case "--cpus" -> {
                    cpus = Double.parseDouble(requireValue("run", a, rest, ++i));
                    i++;
                }
                case "-e", "--env", "--env-file", "-w", "--workdir", "-u", "--user",
                     "--entrypoint", "--restart", "--health-cmd", "--label", "-l",
                     "--add-host", "--device", "--gpus", "--runtime", "--platform",
                     "--pull", "--cidfile", "--hostname", "-h", "--rm", "--init",
                     "--privileged", "--read-only", "--security-opt", "--tmpfs",
                     "--ulimit", "--sysctl", "--ipc", "--pid", "--uts", "--cgroupns",
                     "--expose", "--link", "--dns", "--dns-search", "--dns-option",
                     "--ip", "--mac-address", "--shm-size", "--stop-signal", "--stop-timeout",
                     "--health-interval", "--health-timeout", "--health-retries",
                     "--log-driver", "--log-opt", "--storage-opt", "--isolation" -> {
                    if (takesValue(a) && i + 1 < rest.size() && !rest.get(i + 1).startsWith("-")) {
                        dropped.add(a + " " + rest.get(i + 1));
                        i += 2;
                    } else {
                        dropped.add(a);
                        i++;
                    }
                }
                default -> {
                    if (a.startsWith("-p=") || a.startsWith("--publish=")) {
                        addPort(a.substring(a.indexOf('=') + 1), ports, dropped, warnings, options);
                        i++;
                    } else if (a.startsWith("-v=") || a.startsWith("--volume=")) {
                        volumes.add(a.substring(a.indexOf('=') + 1));
                        i++;
                    } else if (a.startsWith("--name=")) {
                        name = a.substring("--name=".length());
                        i++;
                    } else if (a.startsWith("--network=") || a.startsWith("--net=")) {
                        network = a.substring(a.indexOf('=') + 1);
                        i++;
                    } else if (a.startsWith("-m=") || a.startsWith("--memory=")) {
                        memory = a.substring(a.indexOf('=') + 1);
                        i++;
                    } else if (a.startsWith("--cpus=")) {
                        cpus = Double.parseDouble(a.substring("--cpus=".length()));
                        i++;
                    } else if (a.startsWith("--")) {
                        if (i + 1 < rest.size() && !rest.get(i + 1).startsWith("-") && looksLikeValueFlag(a)) {
                            dropped.add(a + " " + rest.get(i + 1));
                            i += 2;
                        } else {
                            dropped.add(a);
                            i++;
                        }
                    } else {
                        // clustered short flags like -itd
                        for (int c = 1; c < a.length(); c++) {
                            char ch = a.charAt(c);
                            if (ch == 'd') {
                                detachSeen = true;
                            } else if (ch == 'i' || ch == 't') {
                                dropped.add("-" + ch);
                            } else if (ch == 'p' || ch == 'v' || ch == 'm' || ch == 'e' || ch == 'w' || ch == 'u') {
                                // value-taking short option embedded — too ambiguous
                                dropped.add(a);
                                warnings.add("Ambiguous clustered flag '" + a + "'; rewrite as separate options");
                                break;
                            } else {
                                dropped.add("-" + ch);
                            }
                        }
                        i++;
                    }
                }
            }
        }

        if (i >= rest.size()) {
            throw new IllegalArgumentException("docker " + xionCmd + " requires an IMAGE (mapped to a host binary path in Xion)");
        }
        String image = rest.get(i++);
        List<String> cmdArgs = new ArrayList<>(rest.subList(i, rest.size()));

        String binary = mapImageToBinary(image, options, warnings);
        ensureDroppable(dropped, options);

        if (detachSeen) {
            warnings.add("-d/--detach ignored: Xion containers are managed by the daemon");
        }

        List<String> xion = new ArrayList<>();
        xion.add(xionCmd);
        if (name != null) {
            xion.add("--name");
            xion.add(name);
        }
        for (String p : ports) {
            xion.add("-p");
            xion.add(p);
        }
        for (String v : volumes) {
            xion.add("-v");
            xion.add(v);
        }
        if (network != null) {
            xion.add("--network");
            xion.add(network);
        }
        if (memory != null) {
            xion.add("--memory");
            xion.add(memory);
        }
        if (cpus != null) {
            xion.add("--cpus");
            xion.add(Double.toString(cpus));
        }
        xion.add("--");
        xion.add(binary);
        xion.addAll(cmdArgs);

        String summary = "docker " + xionCmd + " … " + image + " → xion " + String.join(" ", xion);
        boolean partial = !dropped.isEmpty() || !warnings.isEmpty();
        return new Translation(xion, dropped, warnings, partial, summary);
    }

    private static void addPort(
            String spec,
            List<String> ports,
            List<String> dropped,
            List<String> warnings,
            Options options) {
        try {
            normalizePortSpec(spec);
            ports.add(spec);
        } catch (IllegalArgumentException ex) {
            if (options.partialPorts || options.allowPartial) {
                dropped.add("-p " + spec);
                warnings.add("Skipped invalid/partial port mapping '" + spec + "': " + ex.getMessage());
            } else {
                throw new IllegalArgumentException(
                        "Invalid port mapping '" + spec + "': " + ex.getMessage()
                                + " (use --partial-ports or --allow-partial to skip)");
            }
        }
    }

    /**
     * Accepts host:container[/proto]. Rejects bare container ports unless partial-ports is used at call site.
     */
    static void normalizePortSpec(String spec) {
        if (spec == null || spec.isBlank()) {
            throw new IllegalArgumentException("empty");
        }
        String ports = spec;
        int slash = spec.lastIndexOf('/');
        if (slash > 0) {
            ports = spec.substring(0, slash);
        }
        // ip:host:container — keep last two
        String[] parts = ports.split(":");
        if (parts.length == 1) {
            throw new IllegalArgumentException(
                    "Xion requires host:container (docker-style published port); got container-only '" + spec + "'");
        }
        if (parts.length > 3) {
            throw new IllegalArgumentException("too many ':' segments");
        }
        String host = parts.length == 3 ? parts[1] : parts[0];
        String container = parts.length == 3 ? parts[2] : parts[1];
        Integer.parseInt(host);
        Integer.parseInt(container);
    }

    private static String convertMount(String mount) {
        // type=bind,source=/host,target=/container[,readonly]
        String source = null;
        String target = null;
        boolean ro = false;
        for (String part : mount.split(",")) {
            String[] kv = part.split("=", 2);
            if (kv.length != 2) {
                continue;
            }
            switch (kv[0].trim()) {
                case "source", "src" -> source = kv[1].trim();
                case "target", "dst", "destination" -> target = kv[1].trim();
                case "readonly", "ro" -> ro = "true".equalsIgnoreCase(kv[1].trim()) || kv[1].isBlank();
                default -> {
                }
            }
        }
        if (source == null || target == null) {
            return null;
        }
        return ro ? source + ":" + target + ":ro" : source + ":" + target;
    }

    private static String mapImageToBinary(String image, Options options, List<String> warnings) {
        String binary = image;
        if (options.stripImageTag && binary.contains(":") && !binary.startsWith("/")) {
            // nginx:1.25 → nginx (caller should pass a real binary path)
            int slash = binary.lastIndexOf('/');
            int colon = binary.lastIndexOf(':');
            if (colon > slash) {
                warnings.add("Stripped image tag from '" + image + "'; Xion runs host binaries, not OCI images — "
                        + "replace with an absolute binary path if needed");
                binary = binary.substring(0, colon);
            }
        }
        if (!binary.startsWith("/") && !binary.contains("/")) {
            warnings.add("Image '" + image + "' mapped to binary '" + binary
                    + "'. Xion does not pull images; ensure this binary exists on PATH or pass an absolute path.");
        }
        return binary;
    }

    private static void ensureDroppable(List<String> dropped, Options options) {
        if (dropped.isEmpty()) {
            return;
        }
        if (options.allowPartial || options.dropUnsupported) {
            return;
        }
        throw new IllegalArgumentException(
                "Unsupported docker flags: " + String.join(", ", unique(dropped))
                        + ". Re-run with --allow-partial or --drop-unsupported to continue.");
    }

    private static List<String> unique(List<String> in) {
        return List.copyOf(new LinkedHashSet<>(in));
    }

    private static String requireValue(String cmd, String flag, List<String> rest, int idx) {
        if (idx >= rest.size() || rest.get(idx).startsWith("-")) {
            throw new IllegalArgumentException("docker " + cmd + ": flag " + flag + " requires a value");
        }
        return rest.get(idx);
    }

    private static boolean takesValue(String flag) {
        return switch (flag) {
            case "-e", "--env", "--env-file", "-w", "--workdir", "-u", "--user",
                 "--entrypoint", "--restart", "--health-cmd", "--label", "-l",
                 "--add-host", "--device", "--gpus", "--runtime", "--platform",
                 "--pull", "--cidfile", "--hostname", "-h", "--security-opt",
                 "--tmpfs", "--ulimit", "--sysctl", "--ipc", "--pid", "--uts",
                 "--cgroupns", "--expose", "--link", "--dns", "--dns-search",
                 "--dns-option", "--ip", "--mac-address", "--shm-size",
                 "--stop-signal", "--stop-timeout", "--health-interval",
                 "--health-timeout", "--health-retries", "--log-driver",
                 "--log-opt", "--storage-opt", "--isolation", "--mount" -> true;
            default -> false;
        };
    }

    private static boolean isFlagWithValue(String flag) {
        return takesValue(flag) || flag.equals("--name") || flag.equals("-p") || flag.equals("--publish")
                || flag.equals("-v") || flag.equals("--volume") || flag.equals("-m") || flag.equals("--memory")
                || flag.equals("--cpus") || flag.equals("--network") || flag.equals("--net")
                || flag.equals("--tail") || flag.equals("--since") || flag.equals("--until");
    }

    private static boolean looksLikeValueFlag(String flag) {
        return !flag.equals("--rm") && !flag.equals("--detach") && !flag.equals("--init")
                && !flag.equals("--privileged") && !flag.equals("--read-only")
                && !flag.equals("--publish-all") && !flag.equals("--help") && !flag.equals("--version");
    }

    public static List<String> normalize(List<String> raw) {
        List<String> args = new ArrayList<>(raw);
        if (!args.isEmpty() && "docker".equalsIgnoreCase(args.getFirst())) {
            args.removeFirst();
        }
        // Support a single string token containing the whole command
        if (args.size() == 1 && args.getFirst().contains(" ")) {
            args = shellSplit(args.getFirst());
            if (!args.isEmpty() && "docker".equalsIgnoreCase(args.getFirst())) {
                args.removeFirst();
            }
        }
        return args;
    }

    /** Minimal shell-ish split for quoted segments. */
    static List<String> shellSplit(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inSingle = false;
        boolean inDouble = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\'' && !inDouble) {
                inSingle = !inSingle;
            } else if (c == '"' && !inSingle) {
                inDouble = !inDouble;
            } else if (Character.isWhitespace(c) && !inSingle && !inDouble) {
                if (!cur.isEmpty()) {
                    tokens.add(cur.toString());
                    cur.setLength(0);
                }
            } else {
                cur.append(c);
            }
        }
        if (!cur.isEmpty()) {
            tokens.add(cur.toString());
        }
        return tokens;
    }
}
