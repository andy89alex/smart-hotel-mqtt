package com.example.smarthotel.sim;

import com.example.smarthotel.common.*;
import com.example.smarthotel.common.payload.*;
import org.eclipse.paho.client.mqttv3.*;
import org.junit.jupiter.api.*;

import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class RoomClientStateTest {
    static final EmbeddedBroker broker = new EmbeddedBroker();
    @AfterAll static void stopBroker() { broker.close(); }
    private final RoomId room = new RoomId("floor1", "room102");

    @Test
    void publishesRetainedStateAndTelemetry() throws Exception {
        RoomClient client = new RoomClient(broker.brokerUrl(), room);
        client.connect();
        client.publishAllState();

        BlockingQueue<Boolean> lightSink = new LinkedBlockingQueue<>();
        MqttClient sub = new MqttClient(broker.brokerUrl(), "sub-" + System.nanoTime(), null);
        sub.connect();
        sub.subscribe(Topics.stateLight(room), 1, (t, m) ->
            lightSink.add(Json.read(m.getPayload(), StatePayload.class).on()));
        assertEquals(Boolean.FALSE, lightSink.poll(5, TimeUnit.SECONDS)); // retained default

        BlockingQueue<Double> tempSink = new LinkedBlockingQueue<>();
        sub.subscribe(Topics.telemetryTemperature(room), 1, (t, m) ->
            tempSink.add(Json.read(m.getPayload(), TemperaturePayload.class).value()));
        client.publishTemperature();
        Double v = tempSink.poll(5, TimeUnit.SECONDS);
        assertNotNull(v);
        assertTrue(v >= 18.0 && v <= 26.0, "temperature in range: " + v);

        client.disconnect();
    }
}
