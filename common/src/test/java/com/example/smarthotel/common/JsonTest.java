package com.example.smarthotel.common;

import com.example.smarthotel.common.payload.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class JsonTest {
    @Test
    void roundTripsTemperature() {
        var p = new TemperaturePayload(22.4, "C", Instant.parse("2026-09-09T10:00:00Z"));
        byte[] bytes = Json.toBytes(p);
        var back = Json.read(bytes, TemperaturePayload.class);
        assertEquals(p, back);
    }

    @Test
    void roundTripsCommand() {
        var c = new CommandPayload(false);
        assertEquals(c, Json.read(Json.toBytes(c), CommandPayload.class));
    }
}
