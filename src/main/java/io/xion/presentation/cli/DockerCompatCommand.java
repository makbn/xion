package io.xion.presentation.cli;

import io.xion.presentation.cli.compat.DockerCommandTranslator;
import jakarta.inject.Inject;
import picocli.CommandLine;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Accepts a Docker CLI invocation, translates it to Xion, prompts for approval, then executes.
 *
 * <pre>
 *   xion docker --yes --allow-partial -- run -d --name web -p 8080:80 nginx:latest
 *   xion docker --dry-run -- "docker run -p 80:80 --memory 256m /usr/bin/python3 -m http.server"
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
                "Translate a Docker CLI command into the equivalent Xion command, show the",
                "mapping (including dropped/unsupported flags), ask for confirmation, then",
                "execute via the local Xion CLI / daemon.",
                "",
                "Xion is not a Docker engine: OCI images are mapped to host binaries, Seatbelt",
                "replaces namespaces, and networking is loopback proxy + bridge DNS. Use",
                "--allow-partial / --drop-unsupported / --partial-ports when the Docker",
                "command cannot be mapped 1:1.",
                "",
                "Supported Docker verbs: run, create, start, stop, logs, ps, network create,",
                "version, help."
        },
        footer = {
                "  # Interactive approval",
                "  xion docker -- run -d --name api -p 8080:80 -v /data:/data --memory 512m nginx",
                "",
                "  # Skip confirmation (CI / scripts)",
                "  xion docker --yes --allow-partial -- run -it --rm -e FOO=1 -p 80:80 httpd",
                "",
                "  # Preview only",
                "  xion docker --dry-run -- \"docker ps -a\"",
                "",
                "  # Skip invalid port specs instead of failing",
                "  xion docker --yes --partial-ports -- run -p 8080 -p 9000:90 /usr/bin/sleep 30",
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
            description = "Do not print dropped-flag / warning details (still shown on decline path).")
    boolean quietDrop;

    @CommandLine.Parameters(
            index = "0..*",
            arity = "1..*",
            paramLabel = "DOCKER_ARGS",
            description = {
                    "Docker command tokens, optionally beginning with the word 'docker'.",
                    "Examples: run -p 8080:80 nginx   OR   \"docker stop web\""
            })
    List<String> dockerArgs = new ArrayList<>();

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    private final DockerCommandTranslator translator = new DockerCommandTranslator();

    @Override
    public Integer call() throws Exception {
        PrintWriter out = spec.commandLine().getOut();
        PrintWriter err = spec.commandLine().getErr();

        DockerCommandTranslator.Options opts = new DockerCommandTranslator.Options();
        opts.allowPartial = allowPartial || dropUnsupported;
        opts.dropUnsupported = dropUnsupported;
        opts.partialPorts = partialPorts;
        opts.stripImageTag = !keepImageTag;

        DockerCommandTranslator.Translation translation;
        try {
            translation = translator.translate(dockerArgs, opts);
        } catch (IllegalArgumentException ex) {
            err.println("docker→xion translation failed: " + ex.getMessage());
            err.println("Hint: pass --allow-partial, --drop-unsupported, and/or --partial-ports.");
            err.println("      See: xion docker --help");
            return 1;
        }

        out.println("Docker → Xion translation");
        out.println("────────────────────────");
        out.println("Input : docker " + String.join(" ", DockerCommandTranslator.normalize(dockerArgs)));
        out.println("Output: xion " + String.join(" ", translation.xionArgs()));
        out.println("Note  : " + translation.summary());
        if (!quietDrop) {
            if (!translation.dropped().isEmpty()) {
                out.println("Dropped unsupported flags:");
                translation.dropped().forEach(d -> out.println("  - " + d));
            }
            if (!translation.warnings().isEmpty()) {
                out.println("Warnings:");
                translation.warnings().forEach(w -> out.println("  ! " + w));
            }
        }
        if (translation.hasDropped() && !(allowPartial || dropUnsupported)) {
            // Translator already throws in this case; belt-and-suspenders.
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
        // Picocli returns command exit code; surface daemon connectivity hints
        if (code != 0 && needsDaemon(translation.xionArgs())) {
            err.println("Tip: ensure the daemon is running: xion daemon start");
            err.println("     socket: " + client.socketPath());
        }
        return code;
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
