package io.xion.infrastructure.network;

import io.xion.domain.PortMapping;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Host → container TCP reverse proxy for {@code -p HOST:CONTAINER}.
 * <p>
 * Userspace byte pump (not kernel publish). Bounded connection workers, large direct
 * buffers, and connect/idle timeouts. Tunables via {@code -Dxion.proxy.max-connections}
 * and {@code -Dxion.proxy.accept-backlog}.
 */
public final class PortProxy implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(PortProxy.class);

    static final int CONNECT_TIMEOUT_MS = Integer.getInteger("xion.proxy.connect-timeout-ms", 5_000);
    static final long IDLE_TIMEOUT_MS = Long.getLong("xion.proxy.idle-timeout-ms", 120_000L);
    static final int PIPE_BUFFER_BYTES = Integer.getInteger("xion.proxy.pipe-buffer-bytes", 256 * 1024);
    static final int SOCKET_BUFFER_BYTES = Integer.getInteger("xion.proxy.socket-buffer-bytes", 1024 * 1024);
    static final int MAX_CONNECTIONS = Integer.getInteger("xion.proxy.max-connections", 256);
    static final int ACCEPT_BACKLOG = Integer.getInteger("xion.proxy.accept-backlog", 512);

    private final Map<Integer, ServerSocketChannel> listeners = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> hostToContainer = new ConcurrentHashMap<>();
    private final Map<Integer, ExecutorService> acceptors = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong hungUpstreamCloses = new AtomicLong();
    private final AtomicLong rejectedConnections = new AtomicLong();
    private final AtomicLong bytesProxied = new AtomicLong();
    private final AtomicInteger activeConnections = new AtomicInteger();
    private final AtomicLong acceptedConnections = new AtomicLong();
    private ThreadPoolExecutor workers;
    private String targetHost = "127.0.0.1";

    public void setTargetHost(String host) {
        this.targetHost = host;
    }

    public String targetHost() {
        return targetHost;
    }

    public long hungUpstreamCloses() {
        return hungUpstreamCloses.get();
    }

    public Stats stats() {
        return new Stats(
                activeConnections.get(),
                acceptedConnections.get(),
                hungUpstreamCloses.get(),
                rejectedConnections.get(),
                bytesProxied.get(),
                MAX_CONNECTIONS,
                Map.copyOf(hostToContainer));
    }

    public record Stats(
            int activeConnections,
            long acceptedConnections,
            long hungUpstreamCloses,
            long rejectedConnections,
            long bytesProxied,
            int maxConnections,
            Map<Integer, Integer> mappings) {
    }

    public synchronized void start(List<PortMapping> mappings, String targetHost) throws IOException {
        setTargetHost(targetHost == null || targetHost.isBlank() ? "127.0.0.1" : targetHost);
        start(mappings);
    }

    public synchronized void start(List<PortMapping> mappings) throws IOException {
        if (running.get()) {
            stop();
        }
        workers = new ThreadPoolExecutor(
                Math.min(32, MAX_CONNECTIONS),
                MAX_CONNECTIONS,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(MAX_CONNECTIONS),
                r -> {
                    Thread t = new Thread(r, "xion-port-proxy-conn");
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.AbortPolicy());
        running.set(true);
        for (PortMapping mapping : mappings) {
            if (!"tcp".equalsIgnoreCase(mapping.protocol())) {
                continue;
            }
            ServerSocketChannel server = ServerSocketChannel.open();
            server.configureBlocking(true);
            try {
                server.setOption(StandardSocketOptions.SO_REUSEADDR, true);
                server.setOption(StandardSocketOptions.SO_RCVBUF, SOCKET_BUFFER_BYTES);
            } catch (IOException ignored) {
                // optional
            }
            server.bind(new InetSocketAddress("0.0.0.0", mapping.hostPort()), ACCEPT_BACKLOG);
            listeners.put(mapping.hostPort(), server);
            hostToContainer.put(mapping.hostPort(), mapping.containerPort());

            ExecutorService acceptor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "xion-port-proxy-accept-" + mapping.hostPort());
                t.setDaemon(true);
                return t;
            });
            acceptors.put(mapping.hostPort(), acceptor);
            int hostPort = mapping.hostPort();
            int containerPort = mapping.containerPort();
            acceptor.submit(() -> acceptLoop(server, hostPort, containerPort));
        }
        LOG.infof(
                "Port proxy listening on %s → %s (maxConns=%d backlog=%d pipe=%dKiB)",
                hostToContainer.keySet(),
                targetHost,
                MAX_CONNECTIONS,
                ACCEPT_BACKLOG,
                PIPE_BUFFER_BYTES / 1024);
    }

    private void acceptLoop(ServerSocketChannel server, int hostPort, int containerPort) {
        while (running.get() && server.isOpen()) {
            try {
                SocketChannel client = server.accept();
                if (client == null) {
                    continue;
                }
                try {
                    workers.submit(() -> handleConnection(client, containerPort));
                } catch (RejectedExecutionException e) {
                    rejectedConnections.incrementAndGet();
                    LOG.warnf("Port proxy rejecting connection on %d — at max-connections (%d)",
                            hostPort, MAX_CONNECTIONS);
                    closeQuietly(client);
                }
            } catch (ClosedChannelException e) {
                break;
            } catch (IOException e) {
                if (running.get()) {
                    LOG.warnf("Port proxy accept error on %d: %s", hostPort, e.getMessage());
                }
            }
        }
    }

    private void handleConnection(SocketChannel client, int containerPort) {
        activeConnections.incrementAndGet();
        acceptedConnections.incrementAndGet();
        SocketChannel upstream = null;
        try {
            tuneSocket(client);
            upstream = SocketChannel.open();
            tuneSocket(upstream);
            upstream.socket().connect(new InetSocketAddress(targetHost, containerPort), CONNECT_TIMEOUT_MS);

            SocketChannel up = upstream;
            SocketChannel cl = client;
            // Pipe threads are not taken from the bounded pool (avoids deadlock).
            Thread reverse = new Thread(() -> pipeOneWay(up, cl), "xion-proxy-up2down");
            reverse.setDaemon(true);
            reverse.start();
            pipeOneWay(cl, up);
            reverse.join(IDLE_TIMEOUT_MS * 2);
        } catch (IOException e) {
            hungUpstreamCloses.incrementAndGet();
            LOG.debugf("Port proxy connection closed (%s:%d): %s", targetHost, containerPort, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            closeQuietly(client);
            closeQuietly(upstream);
            activeConnections.decrementAndGet();
        }
    }

    private void pipeOneWay(SocketChannel src, SocketChannel dst) {
        ByteBuffer buf = ByteBuffer.allocateDirect(PIPE_BUFFER_BYTES);
        long lastActivity = System.nanoTime();
        try {
            src.configureBlocking(true);
            dst.configureBlocking(true);
            src.socket().setSoTimeout((int) Math.min(IDLE_TIMEOUT_MS, Integer.MAX_VALUE));
            while (running.get() && src.isOpen() && dst.isOpen()) {
                buf.clear();
                int read;
                try {
                    read = src.read(buf);
                } catch (java.net.SocketTimeoutException timeout) {
                    long idleMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - lastActivity);
                    if (idleMs >= IDLE_TIMEOUT_MS) {
                        hungUpstreamCloses.incrementAndGet();
                        LOG.warnf(
                                "Port proxy idle timeout to %s (no data for %dms) — upstream may be wedged",
                                targetHost, IDLE_TIMEOUT_MS);
                        break;
                    }
                    continue;
                }
                if (read < 0) {
                    break;
                }
                if (read == 0) {
                    continue;
                }
                lastActivity = System.nanoTime();
                bytesProxied.addAndGet(read);
                buf.flip();
                while (buf.hasRemaining()) {
                    dst.write(buf);
                }
            }
        } catch (IOException ignored) {
            // peer closed
        } finally {
            closeQuietly(src);
            closeQuietly(dst);
        }
    }

    private static void tuneSocket(SocketChannel ch) throws IOException {
        try {
            ch.setOption(StandardSocketOptions.TCP_NODELAY, true);
            ch.setOption(StandardSocketOptions.SO_KEEPALIVE, true);
            ch.setOption(StandardSocketOptions.SO_RCVBUF, SOCKET_BUFFER_BYTES);
            ch.setOption(StandardSocketOptions.SO_SNDBUF, SOCKET_BUFFER_BYTES);
        } catch (IOException ignored) {
            // best-effort
        }
    }

    private static void closeQuietly(SocketChannel ch) {
        if (ch == null) {
            return;
        }
        try {
            ch.close();
        } catch (IOException ignored) {
        }
    }

    public Map<Integer, Integer> mappings() {
        return Map.copyOf(hostToContainer);
    }

    public boolean isRunning() {
        return running.get();
    }

    public synchronized void stop() throws IOException {
        running.set(false);
        for (ExecutorService acceptor : acceptors.values()) {
            acceptor.shutdownNow();
        }
        acceptors.clear();
        for (ServerSocketChannel ch : listeners.values()) {
            ch.close();
        }
        listeners.clear();
        hostToContainer.clear();
        if (workers != null) {
            workers.shutdownNow();
            workers = null;
        }
    }

    @Override
    public void close() throws IOException {
        stop();
    }
}
