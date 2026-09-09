package com.example.smarthotel.common;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TopicsTest {
    private final RoomId r = new RoomId("floor2", "room201");

    @Test
    void buildsTopics() {
        assertEquals("hotel/floor2/room201/telemetry/temperature", Topics.telemetryTemperature(r));
        assertEquals("hotel/floor2/room201/state/light", Topics.stateLight(r));
        assertEquals("hotel/floor2/room201/availability", Topics.availability(r));
        assertEquals("hotel/floor2/room201/cmd/dnd", Topics.cmdDnd(r));
    }

    @Test
    void parsesTopic() {
        String t = "hotel/floor2/room201/state/light";
        assertEquals(new RoomId("floor2", "room201"), Topics.roomIdFromTopic(t));
        assertEquals("state", Topics.category(t));
        assertEquals("light", Topics.leaf(t));
    }
}
