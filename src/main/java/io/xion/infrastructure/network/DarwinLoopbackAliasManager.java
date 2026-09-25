package io.xion.infrastructure.network;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * macOS {@code ifconfig lo0 alias| -alias} implementation.
 * Typically requires elevated privileges; failures surface a clear error to the caller.
 */
@ApplicationScoped
@Typed(DarwinLoopbackAliasManager.class)
public class DarwinLoopbackAliasManager implements LoopbackAliasManager {

    private static final Logger LOG = Logger.getLogger(DarwinLoopbackAliasManager.class);

    @Override
    public void addAlias(String ip, String netmask) throws IOException {
        runIfconfig("alias", ip, netmask);
        LOG.infof("Added lo0 alias %s netmask %s", ip, netmask);
    }

    @Override
    public void removeAlias(String ip) throws IOException {
        // macOS: ifconfig lo0 -alias <ip>
        runIfconfig("-alias", ip, null);
        LOG.infof("Removed lo0 alias %s", ip);
    }

    private static void runIfconfig(String op, String ip, String netmask) throws IOException {
        ProcessBuilder pb;
        if (netmask != null) {
            pb = new ProcessBuilder("ifconfig", "lo0", op, ip, "netmask", netmask);
        } else {
            pb = new ProcessBuilder("ifconfig", "lo0", op, ip);
        }
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String output;
        try {
            output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (!p.waitFor(10, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                throw new IOException("ifconfig timed out while applying lo0 " + op + " " + ip);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            p.destroyForcibly();
            throw new IOException("Interrupted while applying lo0 " + op + " " + ip, e);
        }
        if (p.exitValue() != 0) {
            throw new IOException(
                    "Failed to " + op + " lo0 address " + ip
                            + " (exit " + p.exitValue() + "). "
                            + "Run the Xion daemon with sufficient privileges to manage loopback aliases. "
                            + (output.isBlank() ? "" : "ifconfig: " + output));
        }
    }
}
