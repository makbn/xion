package io.xion.infrastructure.network;

import java.io.IOException;

/**
 * Adds/removes loopback aliases so each container can bind its own IP:port
 * (e.g. {@code 10.89.0.2:8087} vs {@code 10.89.0.3:8087}) on the shared host stack.
 */
public interface LoopbackAliasManager {

    void addAlias(String ip, String netmask) throws IOException;

    void removeAlias(String ip) throws IOException;
}
