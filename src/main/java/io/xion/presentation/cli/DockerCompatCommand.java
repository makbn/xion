package io.xion.presentation.cli;

import io.xion.presentation.cli.compat.DockerCommandTranslator;
import io.xion.presentation.cli.compat.DockerfileTranslator;
import jakarta.inject.Inject;
import picocli.CommandLine;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Accepts a Docker CLI invocation <em>or</em> a Dockerfile, translates the executable
 * surface to Xion, prompts for approval, then executes.
 *
 * <pre>
 *   xion docker --yes --allow-partial -- run -d --name web -p 8080:80 nginx:latest
 *   xion docker --dockerfile Dockerfile --yes --publish-expose --name api
 *   xion docker -f ./Dockerfile --dry-run --volume-host-root /var/xion-data
 * </pre>
 */
@CommandLine.Command(
        name = "docker",
        aliases = {"from-docker", "compat"},
        mixinStandardHelpOptions = true,
        showDefaultValues = true,
        synopsisHeading = "%nUsage:%n  ",
        descriptionHeading = "%nDescription:%n%n",
        optionListHeading = "%nOptions:%n%n",
        parameterListHeading = "%nArguments:%n%n",
        footerHeading = "%nExamples:%n%n",
        description = {
                "Translate a Docker CLI command — or the executable part of a Dockerfile —",
                "into the equivalent Xion command, show the mapping (dropped/unsupported",
                "flags and build-only layers), ask for confirmation, then execute.",
                "",
                "Modes:",
                "  1) CLI:   xion docker -- run|ps|stop|… [docker flags…]",
                "  2) File:  xion docker -f|--dockerfile PATH [options] [-- extra docker-run flags]",
                "",
                "Dockerfile mode reads ENTRYPOINT + CMD (exec or shell form), optionally maps",
                "EXPOSE → -p N:N, VOLUME → -v (with --volume-host-root), and WORKDIR via",
                "sh -c 'cd … && exec …'. FROM/RUN/COPY/ADD/ENV/USER/… are not executed;",
                "they are listed as dropped/ignored because Xion runs host binaries.",
                "",
                "Xion is not a Docker engine. Use --allow-partial / --drop-unsupported /",
                "--partial-ports when the mapping cannot be 1:1.",
                "",
                "Supported Docker verbs: run, create, start, stop, logs, ps, network create,",
                "version, help."
        },
        footer = {
                "  # Interactive approval from a docker run line",
                "  xion docker -- run -d --name api -p 8080:80 -v /data:/data --memory 512m nginx",
                "",
                "  # From a Dockerfile (ENTRYPOINT/CMD → xion run)",
                "  xion docker -f Dockerfile --name web --publish-expose --yes",
                "  xion docker --dockerfile ./deploy/Dockerfile --dry-run --volume-host-root /srv/data",
                "",
                "  # Dockerfile + extra publish / memory overrides",
                "  xion docker -f Dockerfile --name api -- --memory 512m -p 8080:8080",
                "",
                "  # Skip confirmation + allow unsupported flags to be dropped",
                "  xion docker --yes --allow-partial -- run -it --rm -e FOO=1 -p 80:80 httpd",
                "",
                "Exit codes: 0 success, 1 translation/execution error, 2 user declined,",
                "3 partial mapping refused (use --allow-partial)."
        })
public class DockerCompatCommand implements Callable<Integer> {

    @Inject
    DaemonClientSupport client;

    @CommandLine.Option(
            names = {"-y", "--yes", "--assume-yes"},
            description = "Do not prompt; approve and execute the translated command immediately.")
    boolean assumeYes;

    @CommandLine.Option(
            names = {"--dry-run"},
            description = "Print the translation only; do not prompt or execute.")
    boolean dryRun;

    @CommandLine.Option(
            names = {"--allow-partial"},
            description = "Continue when some Docker flags cannot be mapped (they are listed and dropped).")
    boolean allowPartial;

    @CommandLine.Option(
            names = {"--drop-unsupported"},
            description = "Silently drop unsupported Docker flags (implies --allow-partial for flags).")
    boolean dropUnsupported;

    @CommandLine.Option(
            names = {"--partial-ports"},
            description = "Skip invalid or container-only port mappings (-p 80) instead of failing.")
    boolean partialPorts;

    @CommandLine.Option(
            names = {"--keep-image-tag"},
            description = "Do not strip :tag from the image name when mapping to a binary.")
    boolean keepImageTag;

    @CommandLine.Option(
            names = {"--print-only"},
            description = "Print the xion argv as a single shell-escaped line and exit 0 (no execute).")
    boolean printOnly;

    @CommandLine.Option(
            names = {"--quiet-drop", "-q"},
            description = "Do not print dropped-flag / warning details.")
    boolean quietDrop;

    @CommandLine.Option(
            names = {"-f", "--dockerfile"},
            paramLabel = "PATH",
            description = {
                    "Translate the executable part of a Dockerfile (ENTRYPOINT/CMD, plus optional",
                    "EXPOSE/VOLUME/WORKDIR). Mutually exclusive with a leading docker verb unless",
                    "extra run flags are passed after --."
            })
    Path dockerfile;

    @CommandLine.Option(
            names = {"--name"},
            paramLabel = "NAME",
            description = "Container name for Dockerfile mode (passed through as xion run --name).")
    String name;

    @CommandLine.Option(
            names = {"--publish-expose"},
            negatable = true,
            defaultValue = "true",
            fallbackValue = "true",
            description = "Dockerfile mode: map each EXPOSE N to -p N:N (default: true). Use --no-publish-expose to skip.")
    boolean publishExpose;

    @CommandLine.Option(
            names = {"--volume-host-root"},
            paramLabel = "DIR",
            description = "Dockerfile mode: map VOLUME /path → -v DIR/path:/path.")
    String volumeHostRoot;

    @CommandLine.Option(
            names = {"--no-workdir"},
            description = "Dockerfile mode: do not wrap the command with cd WORKDIR.")
    boolean noWorkdir;

    @CommandLine.Option(
            names = {"--memory", "-m"},
            paramLabel = "LIMIT",
            description = "Dockerfile mode: memory limit for the generated run (e.g. 512m).")
    String memory;

    @CommandLine.Option(
            names = {"--cpus"},
            paramLabel = "FLOAT",
            description = "Dockerfile mode: CPU limit for the generated run.")
    Double cpus;

    @CommandLine.Option(
            names = {"--network"},
            paramLabel = "NAME",
            description = "Dockerfile mode: attach generated run to a bridge network.")
    String network;

    @CommandLine.Parameters(
            index = "0..*",
            arity = "0..*",
            paramLabel = "DOCKER_ARGS",
            description = {
                    "Docker command tokens (CLI mode), optionally beginning with 'docker'.",
                    "In Dockerfile mode, tokens after -- are extra docker-run flags merged in",
                    "(e.g. -p 8080:80 --memory 256m). Examples:",
                    "  run -p 8080:80 nginx",
                    "  \"docker stop web\""
            })
    List<String> dockerArgs = new ArrayList<>();

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    private final DockerCommandTranslator translator = new DockerCommandTranslator();
    private final DockerfileTranslator dockerfileTranslator = new DockerfileTranslator();

    @Override
    public Integer call() throws Exception {
        PrintWriter out = spec.commandLine().getOut();
        PrintWriter err = spec.commandLine().getErr();

        if (dockerfile == null && (dockerArgs == null || dockerArgs.isEmpty())) {
            err.println("Specify a docker command or --dockerfile PATH. See: xion docker --help");
            return 1;
        }

        DockerCommandTranslator.Options opts = new DockerCommandTranslator.Options();
        opts.allowPartial = allowPartial || dropUnsupported;
        opts.dropUnsupported = dropUnsupported;
        opts.partialPorts = partialPorts;
        opts.stripImageTag = !keepImageTag;

        DockerCommandTranslator.Translation translation;
        String inputLabel;
        try {
            if (dockerfile != null) {
                DockerfileTranslator.Options fileOpts = new DockerfileTranslator.Options();
                fileOpts.publishExpose = publishExpose;
                fileOpts.volumeHostRoot = volumeHostRoot;
                fileOpts.honorWorkdir = !noWorkdir;
                fileOpts.name = name;
                fileOpts.memory = memory;
                fileOpts.cpus = cpus;
                fileOpts.network = network;
                fileOpts.commandOptions = opts;
                fileOpts.extraRunFlags = extractExtraRunFlags(dockerArgs);
                translation = dockerfileTranslator.translate(dockerfile, fileOpts);
                inputLabel = "Dockerfile " + dockerfile;
            } else {
                translation = translator.translate(dockerArgs, opts);
                inputLabel = "docker " + String.join(" ", DockerCommandTranslator.normalize(dockerArgs));
            }
        } catch (IllegalArgumentException ex) {
            err.println("docker→xion translation failed: " + ex.getMessage());
            err.println("Hint: pass --allow-partial, --drop-unsupported, and/or --partial-ports.");
            err.println("      For Dockerfiles: ensure ENTRYPOINT or CMD is set; see xion docker --help");
            return 1;
        }

        out.println("Docker → Xion translation");
        out.println("────────────────────────");
        out.println("Input : " + inputLabel);
        out.println("Output: xion " + String.join(" ", translation.xionArgs()));
        out.println("Note  : " + translation.summary());
        if (!quietDrop) {
            if (!translation.dropped().isEmpty()) {
                out.println("Dropped / not applied:");
                translation.dropped().forEach(d -> out.println("  - " + d));
            }
            if (!translation.warnings().isEmpty()) {
                out.println("Warnings:");
                translation.warnings().forEach(w -> out.println("  ! " + w));
            }
        }
        if (translation.hasDropped() && dockerfile == null && !(allowPartial || dropUnsupported)) {
            err.println("Refusing partial mapping without --allow-partial / --drop-unsupported.");
            return 3;
        }

        if (dryRun || printOnly) {
            if (printOnly) {
                out.println(shellJoin(translation.xionArgs()));
            } else {
                out.println("(dry-run) not executed");
            }
            return 0;
        }

        if (!assumeYes) {
            out.print("Execute the Xion command above? [y/N] ");
            out.flush();
            String answer = readLine();
            if (answer == null || !(answer.equalsIgnoreCase("y") || answer.equalsIgnoreCase("yes"))) {
                err.println("Aborted.");
                return 2;
            }
        }

        CommandLine root = spec.root().commandLine();
        String[] argv = translation.xionArgs().toArray(String[]::new);
        out.println("Executing: xion " + String.join(" ", argv));
        int code = root.execute(argv);
        if (code != 0 && needsDaemon(translation.xionArgs())) {
            err.println("Tip: ensure the daemon is running: xion daemon start");
            err.println("     socket: " + client.socketPath());
        }
        return code;
    }

    /**
     * Remaining parameters in Dockerfile mode are treated as extra {@code docker run} flags.
     * Leading {@code run}/{@code docker run} words are stripped.
     */
    static List<String> extractExtraRunFlags(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<String> args = new ArrayList<>(DockerCommandTranslator.normalize(raw));
        if (!args.isEmpty() && "run".equalsIgnoreCase(args.getFirst())) {
            args.removeFirst();
        }
        return List.copyOf(args);
    }

    private static boolean needsDaemon(List<String> args) {
        if (args.isEmpty()) {
            return false;
        }
        String cmd = args.getFirst();
        return !cmd.equals("version") && !cmd.equals("--help") && !cmd.equals("help");
    }

    private String readLine() throws Exception {
        if (System.console() != null) {
            return System.console().readLine();
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        return reader.readLine();
    }

    private static String shellJoin(List<String> args) {
        StringBuilder sb = new StringBuilder("xion");
        for (String a : args) {
            sb.append(' ');
            if (a.isEmpty() || a.chars().anyMatch(Character::isWhitespace) || a.contains("'") || a.contains("\"")) {
                sb.append('\'').append(a.replace("'", "'\\''")).append('\'');
            } else {
                sb.append(a);
            }
        }
        return sb.toString();
    }
}
