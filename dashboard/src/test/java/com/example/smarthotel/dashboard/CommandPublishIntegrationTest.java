package com.example.smarthotel.dashboard;

import com.example.smarthotel.common.*;
import com.example.smarthotel.common.payload.*;
import org.eclipse.paho.client.mqttv3.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.Lifecycle;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CommandPublishIntegrationTest {
    // static final so the broker is up before @DynamicPropertySource is read
    static final EmbeddedBroker broker = new EmbeddedBroker();

    // Stop the dashboard's live Paho MQTT endpoints (inbound adapter + outbound
    // handler) before closing the broker, rather than closing the whole Spring
    // context: closing the context here would race with SpringExtension's own
    // afterAll bookkeeping (it still expects the cached context to be usable).
    // Stopping just the MQTT endpoints lets their clients disconnect cleanly so
    // the broker's disappearance doesn't produce "Lost connection" / "Client is
    // not connected" ERROR logs during teardown.
    @Autowired @Qualifier("inbound") Lifecycle mqttInbound;
    @Autowired @Qualifier("mqttOutbound") Lifecycle mqttOutbound;

    @AfterEach
    void stopMqttEndpoints() {
        mqttInbound.stop();
        mqttOutbound.stop();
    }

    @AfterAll
    static void stopBroker() { broker.close(); }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("dashboard.brokerUrl", broker::brokerUrl);
    }

    @Autowired CommandPublisher publisher;
    private final RoomId room = new RoomId("floor3", "room301");

    @Test
    void publishesCommandToBroker() throws Exception {
        MqttClient sub = new MqttClient(broker.brokerUrl(), "sub-" + System.nanoTime(), null);
        sub.connect();
        BlockingQueue<Boolean> sink = new LinkedBlockingQueue<>();
        sub.subscribe(Topics.cmdLight(room), 1, (t, m) ->
            sink.add(Json.read(m.getPayload(), CommandPayload.class).on()));

        publisher.send(room, "light", true);
        assertEquals(Boolean.TRUE, sink.poll(5, TimeUnit.SECONDS));
    }
}
