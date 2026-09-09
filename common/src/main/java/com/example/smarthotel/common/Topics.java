package com.example.smarthotel.common;

public final class Topics {
    public static final String ALL_WILDCARD = "hotel/#";
    private Topics() {}

    private static String base(RoomId r) {
        return "hotel/" + r.floor() + "/" + r.room();
    }

    public static String telemetryTemperature(RoomId r) { return base(r) + "/telemetry/temperature"; }
    public static String stateLight(RoomId r) { return base(r) + "/state/light"; }
    public static String stateAc(RoomId r)    { return base(r) + "/state/ac"; }
    public static String stateDnd(RoomId r)   { return base(r) + "/state/dnd"; }
    public static String availability(RoomId r) { return base(r) + "/availability"; }
    public static String cmdLight(RoomId r) { return base(r) + "/cmd/light"; }
    public static String cmdAc(RoomId r)    { return base(r) + "/cmd/ac"; }
    public static String cmdDnd(RoomId r)   { return base(r) + "/cmd/dnd"; }

    // hotel/{floor}/{room}/{category}[/{leaf}]
    public static RoomId roomIdFromTopic(String topic) {
        String[] p = topic.split("/");
        return new RoomId(p[1], p[2]);
    }
    public static String category(String topic) { return topic.split("/")[3]; }
    public static String leaf(String topic) {
        String[] p = topic.split("/");
        return p[p.length - 1];
    }
}
