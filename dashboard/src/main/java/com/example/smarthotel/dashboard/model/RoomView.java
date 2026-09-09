package com.example.smarthotel.dashboard.model;

public class RoomView {
    public volatile String floor;
    public volatile String room;
    public volatile Double temperature;
    public volatile boolean light;
    public volatile boolean ac;
    public volatile boolean dnd;
    public volatile String availability = "unknown";

    public String key() { return floor + "/" + room; }
}
