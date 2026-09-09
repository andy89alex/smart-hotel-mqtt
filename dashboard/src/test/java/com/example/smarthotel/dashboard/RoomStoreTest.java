package com.example.smarthotel.dashboard;

import com.example.smarthotel.common.*;
import com.example.smarthotel.common.payload.*;
import com.example.smarthotel.dashboard.model.RoomView;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class RoomStoreTest {
    private final RoomId room = new RoomId("floor2", "room201");
    private final RoomStore store = new RoomStore();

    @Test
    void appliesAvailabilityStateAndTemperature() {
        store.apply(Topics.availability(room),
            Json.toBytes(new AvailabilityPayload(AvailabilityPayload.ONLINE, Instant.now())));
        store.apply(Topics.stateLight(room),
            Json.toBytes(new StatePayload(true, Instant.now())));
        RoomView v = store.apply(Topics.telemetryTemperature(room),
            Json.toBytes(new TemperaturePayload(23.5, "C", Instant.now())));

        assertEquals("floor2/room201", v.key());
        assertEquals("online", v.availability);
        assertTrue(v.light);
        assertEquals(23.5, v.temperature);
        assertEquals(1, store.all().size());
    }
}
