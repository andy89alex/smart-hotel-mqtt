package com.example.smarthotel.sim;

import com.example.smarthotel.common.RoomId;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class RoomManager {
    private final SimulatorProperties props;
    private final List<RoomClient> rooms = new ArrayList<>();

    public RoomManager(SimulatorProperties props) { this.props = props; }

    public void startAll() {
        for (var spec : props.rooms()) {
            try {
                RoomClient client = new RoomClient(props.brokerUrl(), new RoomId(spec.floor(), spec.room()));
                client.connect();
                client.publishAllState();
                rooms.add(client);
            } catch (Exception e) { throw new RuntimeException("failed to start " + spec, e); }
        }
    }

    @Scheduled(fixedDelayString = "${simulator.telemetryIntervalMs:5000}")
    public void tick() {
        for (RoomClient r : rooms) {
            try { r.publishTemperature(); } catch (Exception ignored) {}
        }
    }

    public void stopAll() {
        rooms.forEach(RoomClient::disconnect);
        rooms.clear();
    }

    public List<RoomClient> rooms() { return rooms; }
}
