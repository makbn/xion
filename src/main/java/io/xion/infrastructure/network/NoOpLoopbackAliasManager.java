package io.xion.infrastructure.network;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;
import org.jboss.logging.Logger;

/**
 * No-op alias manager for Linux CI / non-Darwin hosts.
 * IP bookkeeping still happens; real aliases are Darwin-only.
 */
@ApplicationScoped
@Typed(NoOpLoopbackAliasManager.class)
public class NoOpLoopbackAliasManager implements LoopbackAliasManager {

    private static final Logger LOG = Logger.getLogger(NoOpLoopbackAliasManager.class);

    @Override
    public void addAlias(String ip, String netmask) {
        LOG.debugf("Skipping lo0 alias add %s (non-Darwin)", ip);
    }

    @Override
    public void removeAlias(String ip) {
        LOG.debugf("Skipping lo0 alias remove %s (non-Darwin)", ip);
    }
}
