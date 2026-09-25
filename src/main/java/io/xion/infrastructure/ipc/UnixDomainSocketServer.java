package io.xion.infrastructure.ipc;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * Unix Domain Socket server for daemon IPC (~/.xion/xion.sock).
 */
@ApplicationScoped
public class UnixDomainSocketServer {

    private static final Logger LOG = Logger.getLogger(UnixDomainSocketServer.class);

    private final Path socketPath;
    private final IpcCodec codec;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile ServerSocketChannel server;
    private ExecutorService acceptor;
    private ExecutorService workers;
    private volatile Function<IpcEnvelope, IpcEnvelope> handler = env ->
            IpcEnvelope.failure(env.type(), env.requestId(), "No handler configured");

    @Inject
    public UnixDomainSocketServer(
            ObjectMapper mapper,
            @ConfigProperty(name = "xion.socket-path") String socketPath) {
        this.codec = new IpcCodec(mapper);
        this.socketPath = Path.of(socketPath);
    }

    public void setHandler(Function<IpcEnvelope, IpcEnvelope> handler) {
        this.handler = handler;
    }

    public Path socketPath() {
        return socketPath;
    }

    public boolean isRunning() {
        return running.get();
    }

    public synchronized void start() throws IOException {
        if (running.get()) {
            return;
        }
        Files.createDirectories(socketPath.getParent());
        Files.deleteIfExists(socketPath);
        server = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
        server.bind(UnixDomainSocketAddress.of(socketPath));
        acceptor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "xion-uds-acceptor");
            t.setDaemon(true);
            return t;
        });
        workers = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "xion-uds-worker");
            t.setDaemon(true);
            return t;
        });
        running.set(true);
        acceptor.submit(this::acceptLoop);
        LOG.infof("Xion daemon listening on %s", socketPath);
    }

    private void acceptLoop() {
        while (running.get()) {
            try {
                SocketChannel client = server.accept();
                workers.submit(() -> handleClient(client));
            } catch (IOException e) {
                if (running.get()) {
                    LOG.warn("UDS accept failed", e);
                }
            }
        }
    }

    private void handleClient(SocketChannel client) {
        try (client) {
            var in = Channels.newInputStream(client);
            var out = Channels.newOutputStream(client);
            IpcEnvelope request = codec.read(in);
            IpcEnvelope response;
            try {
                response = handler.apply(request);
            } catch (Exception ex) {
                response = IpcEnvelope.failure(request.type(), request.requestId(), ex.getMessage());
            }
            codec.write(out, response);
        } catch (IOException e) {
            LOG.debug("UDS client closed", e);
        }
    }

    public synchronized void stop() throws IOException {
        if (!running.getAndSet(false)) {
            return;
        }
        if (server != null) {
            server.close();
        }
        if (acceptor != null) {
            acceptor.shutdownNow();
        }
        if (workers != null) {
            workers.shutdownNow();
        }
        Files.deleteIfExists(socketPath);
        LOG.info("Xion daemon stopped");
    }
}
