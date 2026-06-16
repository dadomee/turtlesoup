package com.turtlesoup.puzzle;

import com.turtlesoup.puzzle.dto.PuzzlePlay;
import com.turtlesoup.puzzle.dto.PuzzleSolution;
import com.turtlesoup.puzzle.dto.PuzzleSummary;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PuzzleController.class)
class PuzzleControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    PuzzleService service;

    @Test
    void listEndpointReturnsSummaries() throws Exception {
        when(service.list()).thenReturn(
            List.of(new PuzzleSummary(1L, "바다거북 스프", Difficulty.HARD, "고전")));

        mockMvc.perform(get("/api/puzzles"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].title").value("바다거북 스프"))
            .andExpect(jsonPath("$[0].solution").doesNotExist());
    }

    @Test
    void playEndpointReturnsScenario() throws Exception {
        when(service.getForPlay(1L)).thenReturn(
            new PuzzlePlay(1L, "바다거북 스프", "상황 텍스트", Difficulty.HARD, "고전"));

        mockMvc.perform(get("/api/puzzles/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.scenario").value("상황 텍스트"))
            .andExpect(jsonPath("$.solution").doesNotExist());
    }

    @Test
    void solutionEndpointReturnsSolution() throws Exception {
        when(service.getSolution(1L)).thenReturn(new PuzzleSolution(1L, "정답 텍스트"));

        mockMvc.perform(get("/api/puzzles/1/solution"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.solution").value("정답 텍스트"));
    }

    @Test
    void missingPuzzleReturns404() throws Exception {
        when(service.getForPlay(99L)).thenThrow(new PuzzleNotFoundException(99L));

        mockMvc.perform(get("/api/puzzles/99"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("문제를 찾을 수 없습니다: 99"));
    }
}
