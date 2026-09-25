package io.xion.presentation.cli;

import picocli.CommandLine;

public class XionVersionProvider implements CommandLine.IVersionProvider {

    public static final String VERSION = "1.0.0-SNAPSHOT";

    @Override
    public String[] getVersion() {
        return new String[] {"xion " + VERSION};
    }
}
