package io.xion.infrastructure.network;

import io.xion.domain.PortMapping;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
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
        try (ServerSocket upstream = new ServerSocket(0)) {
            int containerPort = upstream.getLocalPort();
            ExecutorService pool = Executors.newSingleThreadExecutor();
            pool.submit(() -> echoOnce(upstream));

            int hostPort = freePort();
            try (PortProxy proxy = new PortProxy()) {
                proxy.start(List.of(new PortMapping(hostPort, containerPort, "tcp")), "127.0.0.1");
                assertThat(proxy.isRunning()).isTrue();
                assertThat(proxy.targetHost()).isEqualTo("127.0.0.1");
                Thread.sleep(100);
                assertEcho(hostPort);
            }
            pool.shutdownNow();
        }
    }

    @Test
    void forwardsUsingExplicitTargetHostString() throws Exception {
        // Bind upstream to loopback; proxy must honor targetHost (not a hardcoded constant).
        InetAddress loopback = InetAddress.getByName("127.0.0.1");
        try (ServerSocket upstream = new ServerSocket()) {
            upstream.bind(new InetSocketAddress(loopback, 0));
            int containerPort = upstream.getLocalPort();
            ExecutorService pool = Executors.newSingleThreadExecutor();
            pool.submit(() -> echoOnce(upstream));

            int hostPort = freePort();
            try (PortProxy proxy = new PortProxy()) {
                proxy.start(List.of(new PortMapping(hostPort, containerPort, "tcp")), "127.0.0.1");
                assertThat(proxy.targetHost()).isEqualTo("127.0.0.1");
                assertThat(proxy.mappings()).containsEntry(hostPort, containerPort);
                Thread.sleep(100);
                assertEcho(hostPort);
            }
            pool.shutdownNow();
        }
    }

    @Test
    void startWithTargetHostOverridesDefault() throws Exception {
        int hostPort = freePort();
        int containerPort = freePort();
        try (PortProxy proxy = new PortProxy()) {
            proxy.start(List.of(new PortMapping(hostPort, containerPort, "tcp")), "10.89.0.2");
            assertThat(proxy.targetHost()).isEqualTo("10.89.0.2");
            assertThat(proxy.mappings()).containsEntry(hostPort, containerPort);
        }
    }

    private static void echoOnce(ServerSocket upstream) {
        try (Socket s = upstream.accept()) {
            byte[] buf = s.getInputStream().readNBytes(5);
            s.getOutputStream().write(("echo:" + new String(buf, StandardCharsets.UTF_8))
                    .getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
    }

    private static void assertEcho(int hostPort) throws Exception {
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

    private static int freePort() throws Exception {
        try (ServerSocket probe = new ServerSocket(0)) {
            return probe.getLocalPort();
        }
    }
}
