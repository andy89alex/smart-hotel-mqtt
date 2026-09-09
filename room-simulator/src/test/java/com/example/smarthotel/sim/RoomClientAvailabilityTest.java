package com.example.smarthotel.sim;

import com.example.smarthotel.common.RoomId;
import com.example.smarthotel.common.Topics;
import com.example.smarthotel.common.Json;
import com.example.smarthotel.common.payload.AvailabilityPayload;
import org.eclipse.paho.client.mqttv3.*;
import org.junit.jupiter.api.*;

import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class RoomClientAvailabilityTest {
    static final EmbeddedBroker broker = new EmbeddedBroker();
    @AfterAll static void stopBroker() { broker.close(); }
    private final RoomId room = new RoomId("floor1", "room101");

    private MqttClient subscribe(String topic, BlockingQueue<String> sink) throws Exception {
        MqttClient sub = new MqttClient(broker.brokerUrl(), "sub-" + System.nanoTime(), null);
        sub.connect();
        sub.subscribe(topic, 1, (t, m) ->
            sink.add(Json.read(m.getPayload(), AvailabilityPayload.class).status()));
        return sub;
    }

    @Test
    void publishesRetainedOnlineOnConnect() throws Exception {
        RoomClient client = new RoomClient(broker.brokerUrl(), room);
        client.connect();

        BlockingQueue<String> sink = new LinkedBlockingQueue<>();
        subscribe(Topics.availability(room), sink); // retained → immediate delivery
        assertEquals("online", sink.poll(5, TimeUnit.SECONDS));
        client.disconnect();
    }

    @Test
    void firesOfflineLwtOnCrash() throws Exception {
        RoomClient client = new RoomClient(broker.brokerUrl(), room);
        client.connect();

        BlockingQueue<String> sink = new LinkedBlockingQueue<>();
        subscribe(Topics.availability(room), sink);
        sink.poll(5, TimeUnit.SECONDS); // drain the retained "online"

        client.kill(); // ungraceful → broker publishes LWT
        assertEquals("offline", sink.poll(5, TimeUnit.SECONDS));
    }
}
