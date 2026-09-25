package io.xion.application.handlers;

import java.util.List;

public record ListNetworksResult(List<NetworkInfo> networks) {

    public record NetworkInfo(
            String name,
            String subnet,
            String gateway,
            int memberCount,
            List<String> members) {

        public NetworkInfo(String name, int memberCount, List<String> members) {
            this(name, null, null, memberCount, members);
        }
    }
}
