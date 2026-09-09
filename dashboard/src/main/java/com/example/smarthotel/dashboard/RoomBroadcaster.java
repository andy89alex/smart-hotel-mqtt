package com.example.smarthotel.dashboard;

import com.example.smarthotel.dashboard.model.RoomView;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class RoomBroadcaster {
    private final SimpMessagingTemplate messaging;
    public RoomBroadcaster(SimpMessagingTemplate messaging) { this.messaging = messaging; }
    public void broadcast(RoomView view) { messaging.convertAndSend("/topic/rooms", view); }
}
