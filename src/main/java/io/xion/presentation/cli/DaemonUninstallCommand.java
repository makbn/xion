package io.xion.presentation.cli;

import io.xion.infrastructure.process.LaunchdDaemonInstaller;
import picocli.CommandLine;

import java.util.concurrent.Callable;

@CommandLine.Command(
        name = "uninstall",
        mixinStandardHelpOptions = true,
        synopsisHeading = "%nUsage:%n  ",
        descriptionHeading = "%nDescription:%n%n",
        description = {
                "Remove the launchd agent installed by `xion daemon install`.",
                "",
                "Does not delete ~/.xion data. Stop containers first if needed."
        })
public class DaemonUninstallCommand implements Callable<Integer> {

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public Integer call() throws Exception {
        if (!LaunchdDaemonInstaller.isMac()) {
            spec.commandLine().getErr().println("daemon uninstall is only supported on macOS (launchd).");
            return 1;
        }
        LaunchdDaemonInstaller.uninstall();
        spec.commandLine().getOut().println("Removed launchd agent " + LaunchdDaemonInstaller.LABEL);
        return 0;
    }
}
