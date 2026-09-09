package com.example.smarthotel.dashboard;

import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
public class MqttIngest {
    private final RoomStore store;
    public MqttIngest(RoomStore store) { this.store = store; }

    @ServiceActivator(inputChannel = "mqttInboundChannel")
    public void handle(Message<byte[]> message,
                       @Header(MqttHeaders.RECEIVED_TOPIC) String topic) {
        store.apply(topic, message.getPayload());
    }
}
