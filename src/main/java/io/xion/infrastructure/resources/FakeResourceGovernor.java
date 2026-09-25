package io.xion.infrastructure.resources;

import io.xion.domain.ResourceLimits;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Records applied limits for tests; no OS calls (Linux CI).
 */
@ApplicationScoped
@Typed(FakeResourceGovernor.class)
public class FakeResourceGovernor implements ResourceGovernor {

    private final AtomicReference<ResourceLimits> lastApplied = new AtomicReference<>();

    @Override
    public void apply(ResourceLimits limits) {
        lastApplied.set(limits);
    }

    @Override
    public List<String> wrapCommand(List<String> command, ResourceLimits limits) {
        apply(limits);
        return new ArrayList<>(command);
    }

    public ResourceLimits lastApplied() {
        return lastApplied.get();
    }

    public void clear() {
        lastApplied.set(null);
    }
}
