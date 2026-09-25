package io.xion.application.handlers;

import java.util.List;
import java.util.Map;

public record InspectNetworkResult(
        String name,
        String subnet,
        String gateway,
        List<String> members,
        Map<String, String> endpoints,
        Map<String, String> memberIps) {

    public InspectNetworkResult(String name, List<String> members, Map<String, String> endpoints) {
        this(name, null, null, members, endpoints, Map.of());
    }
}
