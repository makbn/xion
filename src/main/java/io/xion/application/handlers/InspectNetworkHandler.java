package io.xion.application.handlers;

import io.xion.application.mediator.RequestHandler;
import io.xion.domain.NetworkBridge;
import io.xion.infrastructure.network.BridgeResolver;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class InspectNetworkHandler implements RequestHandler<InspectNetworkQuery, InspectNetworkResult> {

    private final BridgeResolver bridges;

    @Inject
    public InspectNetworkHandler(BridgeResolver bridges) {
        this.bridges = bridges;
    }

    @Override
    public Class<InspectNetworkQuery> requestType() {
        return InspectNetworkQuery.class;
    }

    @Override
    public InspectNetworkResult handle(InspectNetworkQuery request) {
        NetworkBridge bridge = bridges.getNetwork(request.name())
                .orElseThrow(() -> new IllegalArgumentException("Network not found: " + request.name()));
        List<String> members = bridge.members().stream().sorted().toList();
        Map<String, String> endpoints = new LinkedHashMap<>();
        for (String member : members) {
            bridges.endpointOf(member).ifPresent(ep -> endpoints.put(member, ep));
        }
        return new InspectNetworkResult(bridge.name(), members, endpoints);
    }
}
