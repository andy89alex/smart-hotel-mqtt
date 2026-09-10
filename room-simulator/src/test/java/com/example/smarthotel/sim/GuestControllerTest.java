package com.example.smarthotel.sim;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GuestController.class)
class GuestControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean RoomManager manager;

    @Test
    void guestActionDelegatesToRoom() throws Exception {
        RoomClient room = mock(RoomClient.class);
        when(manager.find("floor2", "room201")).thenReturn(Optional.of(room));

        mvc.perform(post("/api/guest/floor2/room201/light")
                        .contentType("application/json").content("{\"on\":true}"))
                .andExpect(status().isOk());

        verify(room).guestSet("light", true);
    }

    @Test
    void unknownRoomReturns404() throws Exception {
        when(manager.find(any(), any())).thenReturn(Optional.empty());

        mvc.perform(post("/api/guest/floorX/roomX/light")
                        .contentType("application/json").content("{\"on\":true}"))
                .andExpect(status().isNotFound());
    }
}
