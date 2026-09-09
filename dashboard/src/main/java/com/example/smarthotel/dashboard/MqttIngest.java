package com.example.smarthotel.dashboard;

import com.example.smarthotel.dashboard.model.RoomView;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
public class MqttIngest {
    private final RoomStore store;
    private final RoomBroadcaster broadcaster;
    public MqttIngest(RoomStore store, RoomBroadcaster broadcaster) {
        this.store = store;
        this.broadcaster = broadcaster;
    }

    @ServiceActivator(inputChannel = "mqttInboundChannel")
    public void handle(Message<byte[]> message,
                       @Header(MqttHeaders.RECEIVED_TOPIC) String topic) {
        RoomView updated = store.apply(topic, message.getPayload());
        broadcaster.broadcast(updated);
    }
}
