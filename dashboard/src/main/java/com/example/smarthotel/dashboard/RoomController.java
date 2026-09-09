package com.example.smarthotel.dashboard;

import com.example.smarthotel.common.RoomId;
import com.example.smarthotel.dashboard.model.RoomView;
import com.example.smarthotel.common.payload.CommandPayload;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;

@Controller
public class RoomController {
    private final RoomStore store;
    private final CommandPublisher publisher;
    public RoomController(RoomStore store, CommandPublisher publisher) {
        this.store = store;
        this.publisher = publisher;
    }

    @GetMapping("/")
    public String index() { return "index"; }

    @GetMapping("/api/rooms")
    @ResponseBody
    public Collection<RoomView> rooms() { return store.all(); }

    @PostMapping("/api/rooms/{floor}/{room}/cmd/{device}")
    @ResponseBody
    public void command(@PathVariable String floor, @PathVariable String room,
                        @PathVariable String device, @RequestBody CommandPayload body) {
        publisher.send(new RoomId(floor, room), device, body.on());
    }
}
