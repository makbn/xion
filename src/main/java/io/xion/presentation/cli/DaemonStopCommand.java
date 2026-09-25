package io.xion.presentation.cli;

import io.xion.infrastructure.process.DaemonProcessSupport;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import picocli.CommandLine;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "stop",
        mixinStandardHelpOptions = true,
        synopsisHeading = "%nUsage:%n  ",
        descriptionHeading = "%nDescription:%n%n",
        description = {
                "Stop the background Xion daemon (SIGTERM via pidfile).",
                "",
                "Does not stop containers by itself; use `xion stop` / `xion rm` first if needed."
        })
public class DaemonStopCommand implements Callable<Integer> {

    @Inject
    DaemonClientSupport client;

    @ConfigProperty(name = "xion.runtime-dir")
    String runtimeDir;

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public Integer call() throws Exception {
        Path rt = Path.of(runtimeDir);
        Optional<Long> pid = DaemonProcessSupport.livingDaemonPid(rt);
        if (pid.isEmpty()) {
            // Fall back: reachable socket but missing/stale pidfile (e.g. old foreground session).
            try {
                var response = client.send("ping", client.mapper().createObjectNode());
                if (response.ok()) {
                    spec.commandLine().getErr().println(
                            "Daemon is reachable at " + client.socketPath()
                                    + " but no pidfile was found. Stop the process manually, e.g.:");
                    spec.commandLine().getErr().println(
                            "  kill -TERM $(pgrep -f 'xion.*daemon')");
                    return 1;
                }
            } catch (Exception ignored) {
                // not running
            }
            spec.commandLine().getOut().println("Daemon is not running.");
            return 0;
        }

        long id = pid.get();
        spec.commandLine().getOut().println("Stopping daemon pid " + id + " …");
        boolean stopped = DaemonProcessSupport.stopPid(id, 10_000);
        DaemonProcessSupport.clearPid(rt);
        if (!stopped) {
            spec.commandLine().getErr().println("Failed to stop daemon pid " + id);
            return 1;
        }
        spec.commandLine().getOut().println("Daemon stopped.");
        return 0;
    }
}
