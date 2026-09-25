package io.xion.infrastructure.network;

import io.xion.domain.PortMapping;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Java NIO reverse proxy: host -p 9000:8087 → listen 9000, forward to targetHost:8087
 * (per-container IP when networks assign loopback aliases).
 */
public final class PortProxy implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(PortProxy.class);

    private final Map<Integer, ServerSocketChannel> listeners = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> hostToContainer = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Selector selector;
    private ExecutorService loop;
    private String targetHost = "127.0.0.1";

    public void setTargetHost(String host) {
        this.targetHost = host;
    }

    public String targetHost() {
        return targetHost;
    }

    /**
     * Listen on host ports and reverse-proxy TCP to {@code targetHost}:containerPort.
     */
    public synchronized void start(List<PortMapping> mappings, String targetHost) throws IOException {
        setTargetHost(targetHost == null || targetHost.isBlank() ? "127.0.0.1" : targetHost);
        start(mappings);
    }

    public synchronized void start(List<PortMapping> mappings) throws IOException {
        if (running.get()) {
            stop();
        }
        selector = Selector.open();
        for (PortMapping mapping : mappings) {
            if (!"tcp".equalsIgnoreCase(mapping.protocol())) {
                continue;
            }
            ServerSocketChannel server = ServerSocketChannel.open();
            server.configureBlocking(false);
            server.bind(new InetSocketAddress("0.0.0.0", mapping.hostPort()));
            server.register(selector, SelectionKey.OP_ACCEPT);
            listeners.put(mapping.hostPort(), server);
            hostToContainer.put(mapping.hostPort(), mapping.containerPort());
        }
        running.set(true);
        loop = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "xion-port-proxy");
            t.setDaemon(true);
            return t;
        });
        loop.submit(this::selectLoop);
        LOG.infof("Port proxy listening on %s → %s", hostToContainer.keySet(), targetHost);
    }

    private void selectLoop() {
        while (running.get()) {
            try {
                selector.select(500);
                Iterator<SelectionKey> it = selector.selectedKeys().iterator();
                while (it.hasNext()) {
                    SelectionKey key = it.next();
                    it.remove();
                    if (!key.isValid()) {
                        continue;
                    }
                    if (key.isAcceptable()) {
                        accept(key);
                    } else if (key.isReadable()) {
                        pipe(key);
                    }
                }
            } catch (IOException e) {
                if (running.get()) {
                    LOG.warn("Port proxy select error", e);
                }
            }
        }
    }

    private void accept(SelectionKey key) throws IOException {
        ServerSocketChannel server = (ServerSocketChannel) key.channel();
        SocketChannel client = server.accept();
        if (client == null) {
            return;
        }
        int hostPort = ((InetSocketAddress) server.getLocalAddress()).getPort();
        Integer containerPort = hostToContainer.get(hostPort);
        if (containerPort == null) {
            client.close();
            return;
        }
        SocketChannel upstream = SocketChannel.open();
        upstream.connect(new InetSocketAddress(targetHost, containerPort));
        client.configureBlocking(false);
        upstream.configureBlocking(false);
        client.register(selector, SelectionKey.OP_READ, upstream);
        upstream.register(selector, SelectionKey.OP_READ, client);
    }

    private void pipe(SelectionKey key) throws IOException {
        SocketChannel src = (SocketChannel) key.channel();
        SocketChannel dst = (SocketChannel) key.attachment();
        ByteBuffer buf = ByteBuffer.allocate(8192);
        int read = src.read(buf);
        if (read < 0) {
            src.close();
            dst.close();
            key.cancel();
            return;
        }
        buf.flip();
        while (buf.hasRemaining()) {
            dst.write(buf);
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
        if (selector != null) {
            selector.wakeup();
            selector.close();
        }
        for (ServerSocketChannel ch : listeners.values()) {
            ch.close();
        }
        listeners.clear();
        hostToContainer.clear();
        if (loop != null) {
            loop.shutdownNow();
        }
    }

    @Override
    public void close() throws IOException {
        stop();
    }
}
