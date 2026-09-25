package io.xion.presentation.cli.compat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns a parsed Dockerfile into a synthetic {@code docker run …} argv, then reuses
 * {@link DockerCommandTranslator} so approval / partial flags stay consistent.
 */
public final class DockerfileTranslator {

    private final DockerfileParser parser = new DockerfileParser();
    private final DockerCommandTranslator commandTranslator = new DockerCommandTranslator();

    public static final class Options {
        /** Map EXPOSE N → {@code -p N:N}. */
        public boolean publishExpose = true;
        /** Prefix host side of VOLUME paths: {@code hostRoot + volumePath : volumePath}. */
        public String volumeHostRoot;
        /** Wrap executable with {@code /bin/sh -c 'cd WORKDIR && exec …'}. */
        public boolean honorWorkdir = true;
        public String name;
        public String memory;
        public Double cpus;
        public String network;
        /** Extra docker-run style tokens merged before the image/binary (e.g. -p 8080:80). */
        public List<String> extraRunFlags = List.of();
        public DockerCommandTranslator.Options commandOptions = new DockerCommandTranslator.Options();
    }

    public DockerCommandTranslator.Translation translate(Path dockerfile, Options options) throws IOException {
        DockerfileParser.ParsedDockerfile parsed = parser.parse(dockerfile);
        return translateParsed(parsed, options);
    }

    public DockerCommandTranslator.Translation translateParsed(
            DockerfileParser.ParsedDockerfile parsed,
            Options options) {
        List<String> dropped = new ArrayList<>();
        List<String> warnings = new ArrayList<>(parsed.notes());

        List<String> argv = new ArrayList<>(parsed.executableArgv());
        if (argv.isEmpty()) {
            throw new IllegalArgumentException(
                    "Dockerfile " + parsed.path() + " has no ENTRYPOINT/CMD executable to translate. "
                            + "Add ENTRYPOINT/CMD or pass an override command after --");
        }

        if (options.honorWorkdir && parsed.workdir().isPresent()) {
            String wd = parsed.workdir().get();
            String inner = shellJoin(argv);
            argv = List.of("/bin/sh", "-c", "cd " + shellEscape(wd) + " && exec " + inner);
            warnings.add("WORKDIR " + wd + " applied via /bin/sh -c 'cd … && exec …'");
        }

        for (var instr : parsed.ignoredBuild()) {
            if (List.of("FROM", "RUN", "COPY", "ADD", "MAINTAINER").contains(instr.keyword())) {
                // build-only — mention once
                continue;
            }
            dropped.add(instr.keyword() + (instr.rawArgs().isBlank() ? "" : " " + instr.rawArgs()));
        }
        long buildOnly = parsed.ignoredBuild().stream()
                .filter(i -> List.of("FROM", "RUN", "COPY", "ADD", "MAINTAINER").contains(i.keyword()))
                .count();
        if (buildOnly > 0) {
            warnings.add("Ignored " + buildOnly + " build-only instruction(s) (FROM/RUN/COPY/ADD/…); "
                    + "Xion runs host binaries, it does not build images");
        }

        // Build synthetic: docker run [flags] <binary> [args…]
        List<String> synthetic = new ArrayList<>();
        synthetic.add("run");
        if (options.name != null && !options.name.isBlank()) {
            synthetic.add("--name");
            synthetic.add(options.name);
        }
        if (options.network != null) {
            synthetic.add("--network");
            synthetic.add(options.network);
        }
        if (options.memory != null) {
            synthetic.add("--memory");
            synthetic.add(options.memory);
        }
        if (options.cpus != null) {
            synthetic.add("--cpus");
            synthetic.add(Double.toString(options.cpus));
        }
        for (String env : parsed.envAssignments()) {
            synthetic.add("-e");
            synthetic.add(env);
        }
        if (options.publishExpose) {
            for (Integer port : parsed.exposePorts()) {
                synthetic.add("-p");
                synthetic.add(port + ":" + port);
            }
        } else if (!parsed.exposePorts().isEmpty()) {
            warnings.add("EXPOSE " + parsed.exposePorts() + " not published (pass --publish-expose)");
        }
        for (String vol : parsed.volumes()) {
            if (options.volumeHostRoot != null && !options.volumeHostRoot.isBlank()) {
                String host = joinHost(options.volumeHostRoot, vol);
                synthetic.add("-v");
                synthetic.add(host + ":" + vol);
            } else {
                dropped.add("VOLUME " + vol);
                warnings.add("VOLUME " + vol + " needs a host path; pass --volume-host-root DIR to map "
                        + "DIR" + vol + ":" + vol);
            }
        }
        if (options.extraRunFlags != null) {
            synthetic.addAll(options.extraRunFlags);
        }

        // First argv element is the "image"/binary for docker run translator
        synthetic.add(argv.getFirst());
        if (argv.size() > 1) {
            synthetic.addAll(argv.subList(1, argv.size()));
        }

        DockerCommandTranslator.Options cmdOpts = options.commandOptions == null
                ? new DockerCommandTranslator.Options()
                : options.commandOptions;
        // Dockerfile translation is inherently partial (build layers, USER, …)
        if (!dropped.isEmpty()) {
            cmdOpts.allowPartial = true;
            cmdOpts.dropUnsupported = true;
        }

        DockerCommandTranslator.Translation base = commandTranslator.translate(synthetic, cmdOpts);

        List<String> allDropped = new ArrayList<>(dropped);
        allDropped.addAll(base.dropped());
        List<String> allWarnings = new ArrayList<>(warnings);
        allWarnings.addAll(base.warnings());
        String summary = "Dockerfile " + parsed.path().getFileName()
                + " (ENTRYPOINT/CMD) → xion " + String.join(" ", base.xionArgs());
        return new DockerCommandTranslator.Translation(
                base.xionArgs(),
                allDropped,
                allWarnings,
                true,
                summary);
    }

    private static String joinHost(String root, String containerPath) {
        String r = root.endsWith("/") ? root.substring(0, root.length() - 1) : root;
        String c = containerPath.startsWith("/") ? containerPath : "/" + containerPath;
        return r + c;
    }

    private static String shellJoin(List<String> argv) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < argv.size(); i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(shellEscape(argv.get(i)));
        }
        return sb.toString();
    }

    private static String shellEscape(String s) {
        if (s.isEmpty()) {
            return "''";
        }
        if (s.chars().noneMatch(ch -> Character.isWhitespace(ch) || ch == '\'' || ch == '"' || ch == '$'
                || ch == '`' || ch == '\\')) {
            return s;
        }
        return "'" + s.replace("'", "'\\''") + "'";
    }
}
