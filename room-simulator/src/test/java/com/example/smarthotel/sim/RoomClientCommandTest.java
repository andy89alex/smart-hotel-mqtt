package com.example.smarthotel.sim;

import com.example.smarthotel.common.*;
import com.example.smarthotel.common.payload.*;
import org.eclipse.paho.client.mqttv3.*;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class RoomClientCommandTest {
    static final EmbeddedBroker broker = new EmbeddedBroker();
    @AfterAll static void stopBroker() { broker.close(); }
    private final RoomId room = new RoomId("floor1", "room103");

    @Test
    void commandTurnsLightOnAndRepublishesState() throws Exception {
        RoomClient client = new RoomClient(broker.brokerUrl(), room);
        client.connect(); // must also subscribeToCommands internally

        MqttClient peer = new MqttClient(broker.brokerUrl(), "peer-" + System.nanoTime(), null);
        peer.connect();

        BlockingQueue<Boolean> lightSink = new LinkedBlockingQueue<>();
        peer.subscribe(Topics.stateLight(room), 1, (t, m) ->
            lightSink.add(Json.read(m.getPayload(), StatePayload.class).on()));
        lightSink.poll(3, TimeUnit.SECONDS); // drain retained default (false), if present

        MqttMessage cmd = new MqttMessage(Json.toBytes(new CommandPayload(true)));
        cmd.setQos(1);
        peer.publish(Topics.cmdLight(room), cmd);

        Boolean seen = null;
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            Boolean v = lightSink.poll(5, TimeUnit.SECONDS);
            if (Boolean.TRUE.equals(v)) { seen = v; break; }
        }
        assertEquals(Boolean.TRUE, seen);
        assertTrue(client.light());
        client.disconnect();
    }
}
