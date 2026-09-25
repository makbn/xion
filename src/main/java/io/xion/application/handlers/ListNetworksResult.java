package io.xion.application.handlers;

import java.util.List;

public record ListNetworksResult(List<NetworkInfo> networks) {

    public record NetworkInfo(String name, int memberCount, List<String> members) {
    }
}
