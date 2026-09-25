package io.xion.presentation.cli;

import io.xion.infrastructure.process.DaemonProcessSupport;
import io.xion.infrastructure.process.LaunchdDaemonInstaller;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import picocli.CommandLine;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "install",
        mixinStandardHelpOptions = true,
        synopsisHeading = "%nUsage:%n  ",
        descriptionHeading = "%nDescription:%n%n",
        description = {
                "Install a launchd agent so the daemon survives reboot, logout, and crashes.",
                "",
                "Writes ~/Library/LaunchAgents/io.xion.daemon.plist with KeepAlive=true and",
                "starts it immediately. Prefer this for always-on Mac mini / studio hosts.",
                "macOS only."
        })
public class DaemonInstallCommand implements Callable<Integer> {

    @ConfigProperty(name = "xion.runtime-dir")
    String runtimeDir;

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public Integer call() throws Exception {
        if (!LaunchdDaemonInstaller.isMac()) {
            spec.commandLine().getErr().println("daemon install is only supported on macOS (launchd).");
            return 1;
        }
        Path rt = Path.of(runtimeDir);
        Path log = DaemonProcessSupport.logFile(rt);
        LaunchdDaemonInstaller.install(rt, log);
        spec.commandLine().getOut().println(
                "Installed launchd agent " + LaunchdDaemonInstaller.LABEL
                        + " (" + LaunchdDaemonInstaller.plistPath() + ")");
        spec.commandLine().getOut().println(
                "KeepAlive enabled — daemon restarts after kill/reboot. Log: " + log);
        return 0;
    }
}
