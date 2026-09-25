package io.xion.presentation.cli.compat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts the <em>runtime executable</em> surface of a Dockerfile: ENTRYPOINT, CMD,
 * EXPOSE, VOLUME, WORKDIR, ENV (recorded), ignoring build-only layers (FROM/RUN/COPY/…).
 */
public final class DockerfileParser {

    private static final Pattern KEYWORD = Pattern.compile(
            "^(?i)(ENTRYPOINT|CMD|EXPOSE|VOLUME|WORKDIR|ENV|USER|HEALTHCHECK|SHELL|STOPSIGNAL|LABEL|ARG|ONBUILD)\\b(.*)$");

    public record Instruction(String keyword, String rawArgs, int lineNumber) {
    }

    public record ParsedDockerfile(
            Path path,
            List<String> entrypoint,
            boolean entrypointShell,
            List<String> cmd,
            boolean cmdShell,
            List<Integer> exposePorts,
            List<String> volumes,
            Optional<String> workdir,
            List<String> envAssignments,
            List<Instruction> ignoredBuild,
            List<String> notes
    ) {
        public ParsedDockerfile {
            entrypoint = List.copyOf(entrypoint);
            cmd = List.copyOf(cmd);
            exposePorts = List.copyOf(exposePorts);
            volumes = List.copyOf(volumes);
            envAssignments = List.copyOf(envAssignments);
            ignoredBuild = List.copyOf(ignoredBuild);
            notes = List.copyOf(notes);
            Objects.requireNonNull(path);
            Objects.requireNonNull(workdir);
        }

        /** Docker semantics: argv = ENTRYPOINT + CMD (shell forms become /bin/sh -c …). */
        public List<String> executableArgv() {
            List<String> ep = expand(entrypoint, entrypointShell);
            List<String> c = expand(cmd, cmdShell);
            if (ep.isEmpty() && c.isEmpty()) {
                return List.of();
            }
            if (ep.isEmpty()) {
                return c;
            }
            if (c.isEmpty()) {
                return ep;
            }
            // ENTRYPOINT exec + CMD → append CMD as args; ENTRYPOINT shell already wraps sh -c
            if (entrypointShell) {
                // shell ENTRYPOINT ignores CMD per Docker docs when using shell form oddly;
                // practical approach: prefer ENTRYPOINT shell, note CMD dropped
                return ep;
            }
            List<String> combined = new ArrayList<>(ep);
            combined.addAll(c);
            return List.copyOf(combined);
        }

        private static List<String> expand(List<String> tokens, boolean shell) {
            if (tokens.isEmpty()) {
                return List.of();
            }
            if (shell) {
                return List.of("/bin/sh", "-c", String.join(" ", tokens));
            }
            return tokens;
        }
    }

    public ParsedDockerfile parse(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("Dockerfile not found: " + path.toAbsolutePath());
        }
        List<String> lines = Files.readAllLines(path);
        return parseLines(path, lines);
    }

    public ParsedDockerfile parseLines(Path path, List<String> lines) {
        List<String> entrypoint = List.of();
        boolean entrypointShell = false;
        List<String> cmd = List.of();
        boolean cmdShell = false;
        List<Integer> expose = new ArrayList<>();
        List<String> volumes = new ArrayList<>();
        String workdir = null;
        List<String> envs = new ArrayList<>();
        List<Instruction> ignored = new ArrayList<>();
        List<String> notes = new ArrayList<>();

        String continued = null;
        int continuedFrom = 0;
        for (int i = 0; i < lines.size(); i++) {
            int lineNo = i + 1;
            String raw = lines.get(i);
            String line = stripComment(raw).trim();
            if (line.isEmpty()) {
                continue;
            }
            if (continued != null) {
                line = continued + line;
                lineNo = continuedFrom;
                continued = null;
            }
            if (line.endsWith("\\")) {
                continued = line.substring(0, line.length() - 1).trim() + " ";
                continuedFrom = lineNo;
                continue;
            }

            // Skip FROM / RUN / COPY / ADD / … — build-only
            Matcher m = KEYWORD.matcher(line);
            if (!m.matches()) {
                String first = line.split("\\s+", 2)[0].toUpperCase(Locale.ROOT);
                if (List.of("FROM", "RUN", "COPY", "ADD", "MAINTAINER").contains(first)) {
                    ignored.add(new Instruction(first, line.length() > first.length()
                            ? line.substring(first.length()).trim() : "", lineNo));
                }
                continue;
            }
            String keyword = m.group(1).toUpperCase(Locale.ROOT);
            String args = m.group(2) == null ? "" : m.group(2).trim();
            switch (keyword) {
                case "ENTRYPOINT" -> {
                    var parsed = parseJsonOrShell(args);
                    entrypoint = parsed.tokens();
                    entrypointShell = parsed.shell();
                }
                case "CMD" -> {
                    var parsed = parseJsonOrShell(args);
                    cmd = parsed.tokens();
                    cmdShell = parsed.shell();
                }
                case "EXPOSE" -> expose.addAll(parseExpose(args));
                case "VOLUME" -> volumes.addAll(parseVolume(args));
                case "WORKDIR" -> workdir = unquote(args);
                case "ENV" -> envs.addAll(parseEnv(args));
                case "USER", "HEALTHCHECK", "SHELL", "STOPSIGNAL", "LABEL", "ARG", "ONBUILD" ->
                        ignored.add(new Instruction(keyword, args, lineNo));
                default -> {
                }
            }
        }
        if (continued != null) {
            notes.add("Dockerfile ends with a line continuation; last partial instruction ignored");
        }
        if (entrypoint.isEmpty() && cmd.isEmpty()) {
            notes.add("No ENTRYPOINT or CMD found — cannot derive an executable; supply overrides after --");
        }
        if (entrypointShell && !cmd.isEmpty()) {
            notes.add("Shell-form ENTRYPOINT present: CMD is not appended (Docker shell-form behavior)");
        }

        return new ParsedDockerfile(
                path,
                entrypoint,
                entrypointShell,
                cmd,
                cmdShell,
                expose,
                volumes,
                Optional.ofNullable(workdir),
                envs,
                ignored,
                notes);
    }

    record JsonOrShell(List<String> tokens, boolean shell) {
    }

    static JsonOrShell parseJsonOrShell(String args) {
        String trimmed = args.trim();
        if (trimmed.startsWith("[")) {
            return new JsonOrShell(parseJsonArray(trimmed), false);
        }
        // shell form — keep as a single command string split lightly for display;
        // expand() will join back for /bin/sh -c
        return new JsonOrShell(List.of(trimmed), true);
    }

    static List<String> parseJsonArray(String json) {
        // Minimal JSON string-array parser: ["a","b", "c d"]
        List<String> out = new ArrayList<>();
        String body = json.trim();
        if (!body.startsWith("[") || !body.endsWith("]")) {
            throw new IllegalArgumentException("Invalid exec-form JSON array: " + json);
        }
        body = body.substring(1, body.length() - 1).trim();
        if (body.isEmpty()) {
            return List.of();
        }
        StringBuilder cur = new StringBuilder();
        boolean inQuote = false;
        boolean escape = false;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (escape) {
                cur.append(c);
                escape = false;
                continue;
            }
            if (c == '\\' && inQuote) {
                escape = true;
                continue;
            }
            if (c == '"') {
                inQuote = !inQuote;
                continue;
            }
            if (c == ',' && !inQuote) {
                out.add(cur.toString());
                cur.setLength(0);
                continue;
            }
            if (Character.isWhitespace(c) && !inQuote) {
                continue;
            }
            cur.append(c);
        }
        out.add(cur.toString());
        return List.copyOf(out);
    }

    static List<Integer> parseExpose(String args) {
        List<Integer> ports = new ArrayList<>();
        for (String tok : args.split("\\s+")) {
            if (tok.isBlank()) {
                continue;
            }
            String p = tok;
            int slash = p.indexOf('/');
            if (slash > 0) {
                p = p.substring(0, slash);
            }
            // ranges 8080-8081 → take first only with note by caller
            if (p.contains("-")) {
                p = p.substring(0, p.indexOf('-'));
            }
            ports.add(Integer.parseInt(p));
        }
        return ports;
    }

    static List<String> parseVolume(String args) {
        String trimmed = args.trim();
        if (trimmed.startsWith("[")) {
            return parseJsonArray(trimmed);
        }
        List<String> out = new ArrayList<>();
        for (String tok : trimmed.split("\\s+")) {
            if (!tok.isBlank()) {
                out.add(unquote(tok));
            }
        }
        return out;
    }

    static List<String> parseEnv(String args) {
        List<String> out = new ArrayList<>();
        String trimmed = args.trim();
        if (trimmed.contains("=") && !trimmed.contains(" ")) {
            out.add(trimmed);
            return out;
        }
        // ENV key value  OR  ENV key=value key2=value2
        if (trimmed.contains("=")) {
            for (String tok : DockerCommandTranslator.shellSplit(trimmed)) {
                if (tok.contains("=")) {
                    out.add(tok);
                }
            }
            return out;
        }
        String[] parts = trimmed.split("\\s+", 2);
        if (parts.length == 2) {
            out.add(parts[0] + "=" + parts[1]);
        }
        return out;
    }

    private static String stripComment(String raw) {
        boolean inQuote = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '"') {
                inQuote = !inQuote;
            } else if (c == '#' && !inQuote) {
                return raw.substring(0, i);
            }
        }
        return raw;
    }

    private static String unquote(String s) {
        String t = s.trim();
        if (t.length() >= 2 && t.startsWith("\"") && t.endsWith("\"")) {
            return t.substring(1, t.length() - 1);
        }
        return t;
    }
}
