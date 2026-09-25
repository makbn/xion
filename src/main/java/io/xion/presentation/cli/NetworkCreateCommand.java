package io.xion.presentation.cli;

import jakarta.inject.Inject;
import picocli.CommandLine;

import java.util.concurrent.Callable;

@CommandLine.Command(name = "create", description = "Create a bridge network")
public class NetworkCreateCommand implements Callable<Integer> {

    @Inject
    DaemonClientSupport client;

    @CommandLine.Parameters(index = "0")
    String name;

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public Integer call() throws Exception {
        var response = client.send("network.create", client.object().put("name", name));
        if (!response.ok()) {
            spec.commandLine().getErr().println(response.error());
            return 1;
        }
        spec.commandLine().getOut().println(response.payload().get("name").asText());
        return 0;
    }
}
