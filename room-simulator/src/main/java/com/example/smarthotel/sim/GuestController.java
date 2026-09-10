package com.example.smarthotel.sim;

import com.example.smarthotel.common.payload.CommandPayload;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * Lets you simulate a guest operating a switch inside a room. Unlike the dashboard's
 * command flow (which publishes cmd/*), this makes the room itself the originator of the
 * change — it updates its own state and reports it via state/*, which the dashboard then
 * reflects. Example: POST /api/guest/floor2/room201/light  body {"on": false}
 */
@RestController
public class GuestController {

    private final RoomManager manager;

    public GuestController(RoomManager manager) {
        this.manager = manager;
    }

    @PostMapping("/api/guest/{floor}/{room}/{device}")
    public void guestAction(@PathVariable String floor,
                            @PathVariable String room,
                            @PathVariable String device,
                            @RequestBody CommandPayload body) {
        RoomClient client = manager.find(floor, room)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "unknown room " + floor + "/" + room));
        try {
            client.guestSet(device, body.on());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (MqttException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "publish failed", e);
        }
    }
}
