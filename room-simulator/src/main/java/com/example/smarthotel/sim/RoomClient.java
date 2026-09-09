package com.example.smarthotel.sim;

import com.example.smarthotel.common.Json;
import com.example.smarthotel.common.RoomId;
import com.example.smarthotel.common.Topics;
import com.example.smarthotel.common.payload.AvailabilityPayload;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.time.Instant;

public class RoomClient {
    private final RoomId roomId;
    private final MqttClient client;

    public RoomClient(String brokerUrl, RoomId roomId) throws MqttException {
        this.roomId = roomId;
        // MemoryPersistence avoids Paho's default file-based persistence, which locks a
        // per-clientId/serverURI file on disk. That default caused a real bug in tests:
        // kill() (disconnectForcibly) never calls close(), so the lock file from a killed
        // client wasn't released; a later RoomClient reusing the same room (same client id)
        // then blocked for ~2 minutes waiting on the stale lock. Memory persistence sidesteps
        // this entirely and is also more appropriate for an in-process simulator client.
        this.client = new MqttClient(brokerUrl,
                "room-" + roomId.floor() + "-" + roomId.room(), new MemoryPersistence());
    }

    public RoomId roomId() { return roomId; }

    public void connect() throws MqttException {
        MqttConnectOptions opts = new MqttConnectOptions();
        opts.setCleanSession(true);
        opts.setAutomaticReconnect(false);
        byte[] will = Json.toBytes(new AvailabilityPayload(AvailabilityPayload.OFFLINE, Instant.now()));
        opts.setWill(Topics.availability(roomId), will, 1, true);
        client.connect(opts);
        publishRetained(Topics.availability(roomId),
                new AvailabilityPayload(AvailabilityPayload.ONLINE, Instant.now()));
    }

    protected void publishRetained(String topic, Object payload) throws MqttException {
        MqttMessage msg = new MqttMessage(Json.toBytes(payload));
        msg.setQos(1);
        msg.setRetained(true);
        client.publish(topic, msg);
    }

    public void disconnect() {
        try { client.disconnect(); client.close(); } catch (MqttException ignored) {}
    }

    public void kill() {
        // sendDisconnectPacket=false: a real crash never gets to send a clean MQTT DISCONNECT,
        // and if it did, the broker would treat it as a graceful close and NOT fire the Will —
        // defeating the point of this method. Close the raw socket without notifying the broker.
        try { client.disconnectForcibly(0, 0, false); } catch (MqttException ignored) {}
        try { client.close(true); } catch (MqttException ignored) {}
    }

    protected MqttClient raw() { return client; }
}
