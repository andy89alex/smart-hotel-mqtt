package com.example.smarthotel.sim;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "simulator")
public record SimulatorProperties(String brokerUrl, List<RoomSpec> rooms, long telemetryIntervalMs) {
    public record RoomSpec(String floor, String room) {}
}
