package com.turtlesoup.room;

import com.turtlesoup.puzzle.Difficulty;
import com.turtlesoup.puzzle.Puzzle;
import com.turtlesoup.puzzle.PuzzleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RoomController.class)
class RoomControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean RoomService rooms;
    @MockitoBean PuzzleRepository puzzles;

    @Test
    void createCustomRoomReturnsCodeAndSolution() throws Exception {
        when(rooms.create(eq("호스트"), eq("내문제"), eq("상황"), eq("정답")))
            .thenReturn(new Room("AB12", "호스트", "내문제", "상황", "정답"));

        mockMvc.perform(post("/api/rooms")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"hostName\":\"호스트\",\"title\":\"내문제\",\"scenario\":\"상황\",\"solution\":\"정답\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("AB12"))
            .andExpect(jsonPath("$.solution").value("정답"));
    }

    @Test
    void createFromExistingPuzzleLooksUpSolution() throws Exception {
        when(puzzles.findById(1L)).thenReturn(Optional.of(
            new Puzzle("바다거북 스프", "상황S", "정답S", Difficulty.HARD, "고전")));
        when(rooms.create(eq("호스트"), eq("바다거북 스프"), eq("상황S"), eq("정답S")))
            .thenReturn(new Room("CD34", "호스트", "바다거북 스프", "상황S", "정답S"));

        mockMvc.perform(post("/api/rooms")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"hostName\":\"호스트\",\"puzzleId\":1}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("CD34"))
            .andExpect(jsonPath("$.scenario").value("상황S"));
    }

    @Test
    void blankCustomRoomReturns400() throws Exception {
        // puzzleId 없고 scenario/solution도 비면 400
        mockMvc.perform(post("/api/rooms")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"hostName\":\"호스트\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void getRoomInfoOmitsSolution() throws Exception {
        Room room = new Room("EF56", "호스트", "제목", "상황", "비밀정답");
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
