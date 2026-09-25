package io.xion.infrastructure.process;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Installs a per-user launchd agent so the Xion daemon survives logout, reboot,
 * and unexpected exits ({@code KeepAlive}) — Docker Desktop / brew-services style.
 */
public final class LaunchdDaemonInstaller {

    public static final String LABEL = "io.xion.daemon";

    private LaunchdDaemonInstaller() {
    }

    public static Path plistPath() {
        return Path.of(System.getProperty("user.home"), "Library", "LaunchAgents", LABEL + ".plist");
    }

    public static String domain() {
        return "gui/" + userId();
    }

    public static String serviceTarget() {
        return domain() + "/" + LABEL;
    }

    /**
     * Write plist and {@code launchctl bootstrap} it (bootout first if already loaded).
     */
    public static void install(Path runtimeDir, Path logFile) throws IOException, InterruptedException {
        Files.createDirectories(runtimeDir);
        Files.createDirectories(plistPath().getParent());
        List<String> programArgs = absoluteForegroundCommand();
        String plist = renderPlist(programArgs, runtimeDir, logFile);
        Files.writeString(plistPath(), plist, StandardCharsets.UTF_8);

        // Replace any previous registration.
        runLaunchctl(List.of("bootout", serviceTarget()), true);
        int bootstrap = runLaunchctl(List.of("bootstrap", domain(), plistPath().toString()), false);
        if (bootstrap != 0) {
            // Older macOS / already bootstrapped — try kickstart enable
            int load = runLaunchctl(List.of("load", "-w", plistPath().toString()), false);
            if (load != 0 && bootstrap != 0) {
                throw new IOException(
                        "launchctl bootstrap failed (exit " + bootstrap + "). Try: launchctl bootstrap "
                                + domain() + " " + plistPath());
            }
        }
        runLaunchctl(List.of("enable", serviceTarget()), true);
        runLaunchctl(List.of("kickstart", "-k", serviceTarget()), true);
    }

    public static void uninstall() throws IOException, InterruptedException {
        runLaunchctl(List.of("bootout", serviceTarget()), true);
        runLaunchctl(List.of("unload", "-w", plistPath().toString()), true);
        Files.deleteIfExists(plistPath());
    }

    public static boolean isInstalled() {
        return Files.isRegularFile(plistPath());
    }

    public static boolean isLoaded() {
        try {
            int code = runLaunchctl(List.of("print", serviceTarget()), true);
            return code == 0;
        } catch (Exception e) {
            return false;
        }
    }

    static List<String> absoluteForegroundCommand() {
        List<String> raw = DaemonProcessSupport.foregroundRelaunchCommand();
        List<String> abs = new ArrayList<>();
        // First element is executable — make absolute when possible
        Path exe = Path.of(raw.getFirst());
        if (!exe.isAbsolute()) {
            String pathEnv = System.getenv("PATH");
            if (pathEnv != null) {
                for (String dir : pathEnv.split(":")) {
                    Path candidate = Path.of(dir, raw.getFirst());
                    if (Files.isExecutable(candidate)) {
                        exe = candidate.toAbsolutePath().normalize();
                        break;
                    }
                }
            }
        } else {
            exe = exe.toAbsolutePath().normalize();
        }
        abs.add(exe.toString());
        for (int i = 1; i < raw.size(); i++) {
            String a = raw.get(i);
            if (a.endsWith(".jar") || a.endsWith(".jar\"")) {
                Path jar = Path.of(a.replace("\"", ""));
                if (!jar.isAbsolute() && Files.exists(jar)) {
                    a = jar.toAbsolutePath().normalize().toString();
                }
            }
            abs.add(a);
        }
        return abs;
    }

    static String renderPlist(List<String> programArgs, Path workingDir, Path logFile) {
        StringBuilder args = new StringBuilder();
        for (String a : programArgs) {
            args.append("    <string>").append(xmlEscape(a)).append("</string>\n");
        }
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
                <plist version="1.0">
                <dict>
                  <key>Label</key>
                  <string>%s</string>
                  <key>ProgramArguments</key>
                  <array>
                %s  </array>
                  <key>RunAtLoad</key>
                  <true/>
                  <key>KeepAlive</key>
                  <true/>
                  <key>WorkingDirectory</key>
                  <string>%s</string>
                  <key>StandardOutPath</key>
                  <string>%s</string>
                  <key>StandardErrorPath</key>
                  <string>%s</string>
                  <key>EnvironmentVariables</key>
                  <dict>
                    <key>PATH</key>
                    <string>/usr/local/bin:/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin</string>
                  </dict>
                </dict>
                </plist>
                """.formatted(
                LABEL,
                args,
                xmlEscape(workingDir.toAbsolutePath().normalize().toString()),
                xmlEscape(logFile.toAbsolutePath().normalize().toString()),
                xmlEscape(logFile.toAbsolutePath().normalize().toString()));
    }

    private static String xmlEscape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String userId() {
        try {
            Process p = new ProcessBuilder("id", "-u").redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            p.waitFor(5, TimeUnit.SECONDS);
            return out.isEmpty() ? String.valueOf(ProcessHandle.current().pid()) : out;
        } catch (Exception e) {
            return "501";
        }
    }

    private static int runLaunchctl(List<String> args, boolean ignoreFailure)
            throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<>();
        cmd.add("launchctl");
        cmd.addAll(args);
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        p.getInputStream().readAllBytes();
        boolean finished = p.waitFor(15, TimeUnit.SECONDS);
        if (!finished) {
            p.destroyForcibly();
            if (!ignoreFailure) {
                throw new IOException("launchctl timed out: " + cmd);
            }
            return -1;
        }
        int code = p.exitValue();
        if (code != 0 && !ignoreFailure) {
            return code;
        }
        return code;
    }

    public static boolean isMac() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
    }
}
