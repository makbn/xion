package io.xion.infrastructure.network;

import io.xion.domain.PortMapping;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class PortProxyTest {

    @Test
    void forwardsHostPortToContainerLoopback() throws Exception {
        // Upstream "container" service on an ephemeral port
        try (ServerSocket upstream = new ServerSocket(0)) {
            int containerPort = upstream.getLocalPort();
            ExecutorService pool = Executors.newSingleThreadExecutor();
            pool.submit(() -> {
                try (Socket s = upstream.accept()) {
                    byte[] buf = s.getInputStream().readNBytes(5);
                    s.getOutputStream().write(("echo:" + new String(buf, StandardCharsets.UTF_8))
                            .getBytes(StandardCharsets.UTF_8));
                } catch (Exception ignored) {
                }
            });

            try (ServerSocket probe = new ServerSocket(0)) {
                int hostPort = probe.getLocalPort();
                probe.close();

                try (PortProxy proxy = new PortProxy()) {
                    proxy.start(List.of(new PortMapping(hostPort, containerPort, "tcp")), "127.0.0.1");
                    assertThat(proxy.isRunning()).isTrue();
                    Thread.sleep(100);

                    try (Socket client = new Socket()) {
                        client.connect(new InetSocketAddress("127.0.0.1", hostPort), 2000);
                        OutputStream out = client.getOutputStream();
                        InputStream in = client.getInputStream();
                        out.write("hello".getBytes(StandardCharsets.UTF_8));
                        out.flush();
                        byte[] resp = in.readNBytes(10);
                        assertThat(new String(resp, StandardCharsets.UTF_8)).isEqualTo("echo:hello");
                    }
                }
            }
            pool.shutdownNow();
        }
    }

    @Test
    void targetHostIsRecordedInMappings() throws Exception {
        try (ServerSocket upstream = new ServerSocket()) {
            upstream.bind(new InetSocketAddress("127.0.0.1", 0));
            int containerPort = upstream.getLocalPort();
            try (ServerSocket probe = new ServerSocket(0)) {
                int hostPort = probe.getLocalPort();
                probe.close();
                try (PortProxy proxy = new PortProxy()) {
                    proxy.start(List.of(new PortMapping(hostPort, containerPort, "tcp")), "127.0.0.1");
                    assertThat(proxy.mappings()).containsEntry(hostPort, containerPort);
                }
            }
        }
    }
}
