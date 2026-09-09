package com.example.smarthotel.dashboard.model;

public class RoomView {
    public String floor;
    public String room;
    public Double temperature;
    public boolean light;
    public boolean ac;
    public boolean dnd;
    public String availability = "unknown";

    public String key() { return floor + "/" + room; }
}
