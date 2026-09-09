package com.example.smarthotel.sim;

import com.example.smarthotel.common.RoomId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class RoomManager {
    private static final Logger log = LoggerFactory.getLogger(RoomManager.class);

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
            } catch (Exception e) {
                // Don't leak connections already opened for earlier rooms in this startAll() call.
                stopAll();
                throw new RuntimeException("failed to start " + spec, e);
            }
        }
    }

    @Scheduled(fixedDelayString = "${simulator.telemetryIntervalMs:5000}")
    public void tick() {
        for (RoomClient r : rooms) {
            try {
                r.publishTemperature();
            } catch (Exception e) {
                log.warn("failed to publish temperature for room {}", r.roomId(), e);
            }
        }
    }

    public void stopAll() {
        rooms.forEach(RoomClient::disconnect);
        rooms.clear();
    }

    public List<RoomClient> rooms() { return rooms; }
}
