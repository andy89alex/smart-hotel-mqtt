package com.example.smarthotel.common.payload;
import java.time.Instant;
public record AvailabilityPayload(String status, Instant ts) {
    public static final String ONLINE = "online";
    public static final String OFFLINE = "offline";
}
