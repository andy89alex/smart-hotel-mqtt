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
        subscribeToCommands();
    }

    public void subscribeToCommands() throws MqttException {
        client.subscribe(Topics.cmdLight(roomId), 1, (t, m) -> applyCommand("light", m));
        client.subscribe(Topics.cmdAc(roomId),    1, (t, m) -> applyCommand("ac", m));
        client.subscribe(Topics.cmdDnd(roomId),   1, (t, m) -> applyCommand("dnd", m));
    }

    private void applyCommand(String which, MqttMessage m) {
        boolean on = Json.read(m.getPayload(), com.example.smarthotel.common.payload.CommandPayload.class).on();
        try {
            switch (which) {
                case "light" -> { light = on; publishRetained(Topics.stateLight(roomId), state(on)); }
                case "ac"    -> { ac = on;    publishRetained(Topics.stateAc(roomId), state(on)); }
                case "dnd"   -> { dnd = on;   publishRetained(Topics.stateDnd(roomId), state(on)); }
            }
        } catch (MqttException e) { throw new RuntimeException(e); }
    }

    private com.example.smarthotel.common.payload.StatePayload state(boolean on) {
        return new com.example.smarthotel.common.payload.StatePayload(on, java.time.Instant.now());
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

    private volatile boolean light = false;
    private volatile boolean ac = false;
    private volatile boolean dnd = false;
    private final java.util.Random rnd = new java.util.Random();
    private double temperature = 22.0;

    public boolean light() { return light; }
    public boolean ac() { return ac; }
    public boolean dnd() { return dnd; }

    public void publishAllState() throws MqttException {
        publishRetained(Topics.stateLight(roomId), new com.example.smarthotel.common.payload.StatePayload(light, java.time.Instant.now()));
        publishRetained(Topics.stateAc(roomId),    new com.example.smarthotel.common.payload.StatePayload(ac, java.time.Instant.now()));
        publishRetained(Topics.stateDnd(roomId),   new com.example.smarthotel.common.payload.StatePayload(dnd, java.time.Instant.now()));
    }

    public void publishTemperature() throws MqttException {
        temperature += (rnd.nextDouble() - 0.5); // drift ±0.5
        if (temperature < 18) temperature = 18;
        if (temperature > 26) temperature = 26;
        double rounded = Math.round(temperature * 10.0) / 10.0;
        MqttMessage msg = new MqttMessage(Json.toBytes(
            new com.example.smarthotel.common.payload.TemperaturePayload(rounded, "C", java.time.Instant.now())));
        msg.setQos(1);
        msg.setRetained(false);
        raw().publish(Topics.telemetryTemperature(roomId), msg);
    }
}
