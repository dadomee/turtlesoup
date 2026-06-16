package com.turtlesoup.ai;

import com.turtlesoup.puzzle.PuzzleNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AiController.class)
class AiControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AiJudgeService service;

    @Test
    void askReturnsVerdict() throws Exception {
        when(service.judge(eq(1L), anyString())).thenReturn(Verdict.YES);

        mockMvc.perform(post("/api/ai/1/ask")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"주인공이 죽었나요?\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.verdict").value("YES"));
    }

    @Test
    void missingPuzzleReturns404() throws Exception {
        when(service.judge(eq(99L), anyString())).thenThrow(new PuzzleNotFoundException(99L));

        mockMvc.perform(post("/api/ai/99/ask")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"x\"}"))
            .andExpect(status().isNotFound());
    }

    @Test
    void blankQuestionReturns400() throws Exception {
        mockMvc.perform(post("/api/ai/1/ask")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void hintEndpointReturnsGeneratedHint() throws Exception {
        when(service.hint(1L, 2)).thenReturn("이 사건은 보이는 것과 다릅니다.");
        mockMvc.perform(post("/api/ai/1/hint/2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.n").value(2))
            .andExpect(jsonPath("$.hint").value("이 사건은 보이는 것과 다릅니다."));
    }

    @Test
    void hintMissingPuzzleReturns404() throws Exception {
        when(service.hint(99L, 1)).thenThrow(new PuzzleNotFoundException(99L));
        mockMvc.perform(post("/api/ai/99/hint/1"))
            .andExpect(status().isNotFound());
    }
}
