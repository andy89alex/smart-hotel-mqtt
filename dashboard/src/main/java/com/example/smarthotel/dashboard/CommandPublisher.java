package com.example.smarthotel.dashboard;

import com.example.smarthotel.common.*;
import com.example.smarthotel.common.payload.CommandPayload;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

@Component
public class CommandPublisher {
    private final MessageChannel mqttOutboundChannel;
    public CommandPublisher(MessageChannel mqttOutboundChannel) {
        this.mqttOutboundChannel = mqttOutboundChannel;
    }

    public void send(RoomId room, String device, boolean on) {
        String topic = switch (device) {
            case "light" -> Topics.cmdLight(room);
            case "ac"    -> Topics.cmdAc(room);
            case "dnd"   -> Topics.cmdDnd(room);
            default -> throw new IllegalArgumentException("unknown device: " + device);
        };
        mqttOutboundChannel.send(MessageBuilder
            .withPayload(Json.toBytes(new CommandPayload(on)))
            .setHeader("mqtt_topic", topic)
            .build());
    }
}
