package io.xion.application.handlers;

import io.xion.application.mediator.RequestHandler;
import io.xion.domain.NetworkBridge;
import io.xion.infrastructure.network.BridgeResolver;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class ListNetworksHandler implements RequestHandler<ListNetworksQuery, ListNetworksResult> {

    private final BridgeResolver bridges;

    @Inject
    public ListNetworksHandler(BridgeResolver bridges) {
        this.bridges = bridges;
    }

    @Override
    public Class<ListNetworksQuery> requestType() {
        return ListNetworksQuery.class;
    }

    @Override
    public ListNetworksResult handle(ListNetworksQuery request) {
        List<ListNetworksResult.NetworkInfo> infos = new ArrayList<>();
        for (NetworkBridge bridge : bridges.listNetworks()) {
            List<String> members = bridge.members().stream().sorted().toList();
            infos.add(new ListNetworksResult.NetworkInfo(
                    bridge.name(),
                    bridge.cidr().orElse(null),
                    bridge.gateway().orElse(null),
                    members.size(),
                    members));
        }
        return new ListNetworksResult(infos);
    }
}
