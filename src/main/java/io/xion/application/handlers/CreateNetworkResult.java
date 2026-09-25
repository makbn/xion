package io.xion.application.handlers;

public record CreateNetworkResult(String name, String subnet, String gateway) {

    public CreateNetworkResult(String name) {
        this(name, null, null);
    }
}
