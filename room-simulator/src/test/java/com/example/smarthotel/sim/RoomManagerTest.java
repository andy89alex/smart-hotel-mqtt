package com.example.smarthotel.sim;

import org.junit.jupiter.api.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RoomManagerTest {
    static final EmbeddedBroker broker = new EmbeddedBroker();
    @AfterAll static void stopBroker() { broker.close(); }

    @Test
    void startsAllConfiguredRooms() throws Exception {
        var props = new SimulatorProperties(
            broker.brokerUrl(),
            List.of(new SimulatorProperties.RoomSpec("floor1", "room101"),
                    new SimulatorProperties.RoomSpec("floor1", "room102")),
            5000L);
        RoomManager manager = new RoomManager(props);
        manager.startAll();
        assertEquals(2, manager.rooms().size());
        manager.tick(); // publishes temperature to all without throwing
        manager.stopAll();
    }
}
