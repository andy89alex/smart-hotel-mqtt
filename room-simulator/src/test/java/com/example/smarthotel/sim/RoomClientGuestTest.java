package com.example.smarthotel.sim;

import com.example.smarthotel.common.Json;
import com.example.smarthotel.common.RoomId;
import com.example.smarthotel.common.Topics;
import com.example.smarthotel.common.payload.StatePayload;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class RoomClientGuestTest {
    static final EmbeddedBroker broker = new EmbeddedBroker();
    @AfterAll static void stopBroker() { broker.close(); }
    private final RoomId room = new RoomId("floor1", "room109");

    @Test
    void guestSwitchTurnsAcOffAndPublishesState() throws Exception {
        RoomClient client = new RoomClient(broker.brokerUrl(), room);
        client.connect();
        client.guestSet("ac", true);   // AC starts on

        MqttClient peer = new MqttClient(broker.brokerUrl(), "peer-" + System.nanoTime(), null);
        peer.connect();
        BlockingQueue<Boolean> acSink = new LinkedBlockingQueue<>();
        peer.subscribe(Topics.stateAc(room), 1, (t, m) ->
            acSink.add(Json.read(m.getPayload(), StatePayload.class).on()));
        acSink.poll(3, TimeUnit.SECONDS); // drain the retained "true"

        client.guestSet("ac", false);  // guest flips the switch off — room initiates

        Boolean seen = null;
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            Boolean v = acSink.poll(5, TimeUnit.SECONDS);
            if (Boolean.FALSE.equals(v)) { seen = v; break; }
        }
        assertEquals(Boolean.FALSE, seen, "dashboard-side subscriber should receive state/ac=false");
        assertFalse(client.ac(), "room's own internal AC state should be off");
        client.disconnect();
    }

    @Test
    void guestSwitchRejectsUnknownDevice() throws Exception {
        RoomClient client = new RoomClient(broker.brokerUrl(), room);
        client.connect();
        assertThrows(IllegalArgumentException.class, () -> client.guestSet("heater", true));
        client.disconnect();
    }
}
