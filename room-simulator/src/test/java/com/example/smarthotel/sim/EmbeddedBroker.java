package com.example.smarthotel.sim;

import io.moquette.broker.Server;
import io.moquette.broker.config.MemoryConfig;

import java.net.ServerSocket;
import java.util.Properties;

/** In-process MQTT broker for tests — starts on a random free port, no Docker. */
public class EmbeddedBroker implements AutoCloseable {
    private final Server server = new Server();
    private final int port;

    public EmbeddedBroker() {
        this.port = freePort();
        Properties props = new Properties();
        props.setProperty("host", "127.0.0.1");
        props.setProperty("port", Integer.toString(port));
        props.setProperty("allow_anonymous", "true");
        props.setProperty("persistence_enabled", "false");
        try {
            server.startServer(new MemoryConfig(props));
        } catch (Exception e) {
            throw new RuntimeException("failed to start embedded broker", e);
        }
    }

    public String brokerUrl() { return "tcp://127.0.0.1:" + port; }

    private static int freePort() {
        try (ServerSocket s = new ServerSocket(0)) { return s.getLocalPort(); }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    @Override public void close() { server.stopServer(); }
}
