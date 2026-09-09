package com.example.smarthotel.dashboard;

import com.example.smarthotel.common.*;
import com.example.smarthotel.common.payload.*;
import com.example.smarthotel.dashboard.model.RoomView;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RoomStore {
    private final Map<String, RoomView> rooms = new ConcurrentHashMap<>();

    public RoomView apply(String topic, byte[] payload) {
        RoomId id = Topics.roomIdFromTopic(topic);
        RoomView v = rooms.computeIfAbsent(id.floor() + "/" + id.room(), k -> {
            RoomView nv = new RoomView();
            nv.floor = id.floor();
            nv.room = id.room();
            return nv;
        });
        String category = Topics.category(topic);
        switch (category) {
            case "availability" -> v.availability = Json.read(payload, AvailabilityPayload.class).status();
            case "telemetry"   -> v.temperature = Json.read(payload, TemperaturePayload.class).value();
            case "state" -> {
                boolean on = Json.read(payload, StatePayload.class).on();
                switch (Topics.leaf(topic)) {
                    case "light" -> v.light = on;
                    case "ac"    -> v.ac = on;
                    case "dnd"   -> v.dnd = on;
                }
            }
        }
        return v;
    }

    public Collection<RoomView> all() { return rooms.values(); }
}
