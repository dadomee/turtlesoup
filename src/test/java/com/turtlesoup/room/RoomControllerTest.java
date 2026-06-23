package com.turtlesoup.room;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RoomController.class)
class RoomControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    RoomService rooms;

    @Test
    void createReturnsCode() throws Exception {
        when(rooms.create(eq("호스트"), eq(false))).thenReturn(new Room("AB12", "호스트"));
        mockMvc.perform(post("/api/rooms")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"hostName\":\"호스트\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("AB12"));
    }

    @Test
    void createAiRoom() throws Exception {
        when(rooms.create(eq("호스트"), eq(true))).thenReturn(new Room("CD34", "호스트"));
        mockMvc.perform(post("/api/rooms")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"hostName\":\"호스트\",\"aiHosted\":true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("CD34"));
    }

    @Test
    void blankHostReturns400() throws Exception {
        mockMvc.perform(post("/api/rooms")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"hostName\":\"\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void getRoomInfoOmitsSolution() throws Exception {
        Room room = new Room("EF56", "호스트");
        room.setPuzzle("제목", "상황", "비밀정답");
        room.addParticipant("철수");
        when(rooms.get("EF56")).thenReturn(room);
        mockMvc.perform(get("/api/rooms/EF56"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.scenario").value("상황"))
            .andExpect(jsonPath("$.participants[0]").value("철수"))
            .andExpect(jsonPath("$.solution").doesNotExist());
    }

    @Test
    void missingRoomReturns404() throws Exception {
        when(rooms.get("ZZZZ")).thenThrow(new RoomNotFoundException("ZZZZ"));
        mockMvc.perform(get("/api/rooms/ZZZZ"))
            .andExpect(status().isNotFound());
    }
}
