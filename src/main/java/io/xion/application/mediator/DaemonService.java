package io.xion.application.mediator;

import io.xion.infrastructure.ipc.UnixDomainSocketServer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

@ApplicationScoped
public class DaemonService {

    private static final Logger LOG = Logger.getLogger(DaemonService.class);

    private final UnixDomainSocketServer server;
    private final IpcDispatcher dispatcher;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final CountDownLatch shutdown = new CountDownLatch(1);

    @Inject
    public DaemonService(UnixDomainSocketServer server, IpcDispatcher dispatcher) {
        this.server = server;
        this.dispatcher = dispatcher;
    }

    public void startAndBlock() throws IOException, InterruptedException {
        start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                stop();
            } catch (IOException e) {
                LOG.warn("Error stopping daemon on shutdown", e);
            }
        }));
        shutdown.await();
    }

    public void start() throws IOException {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        Files.createDirectories(server.socketPath().getParent());
        server.setHandler(dispatcher::dispatch);
        server.start();
        LOG.info("Xion daemon started");
    }

    public void stop() throws IOException {
        if (!started.compareAndSet(true, false)) {
            return;
        }
        server.stop();
        shutdown.countDown();
        LOG.info("Xion daemon stopped");
    }

    public boolean isRunning() {
        return started.get() && server.isRunning();
    }
}
