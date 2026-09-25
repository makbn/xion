package io.xion.application.handlers;

import java.util.List;
import java.util.Map;

public record InspectNetworkResult(String name, List<String> members, Map<String, String> endpoints) {
}
