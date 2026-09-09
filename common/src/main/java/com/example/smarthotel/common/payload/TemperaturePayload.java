package com.example.smarthotel.common.payload;
import java.time.Instant;
public record TemperaturePayload(double value, String unit, Instant ts) {}
