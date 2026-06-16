package com.turtlesoup.history;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HistoryController.class)
class HistoryControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    PlayHistoryService service;

    @Test
    void recordsSolve() throws Exception {
        mockMvc.perform(post("/api/ai/1/solve")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"다솜\",\"questionCount\":5}"))
            .andExpect(status().isOk());

        verify(service).recordSolve(eq(1L), eq(GameMode.AI), eq("다솜"), eq(5));
    }

    @Test
    void blankNicknameReturns400() throws Exception {
        mockMvc.perform(post("/api/ai/1/solve")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"\",\"questionCount\":5}"))
            .andExpect(status().isBadRequest());
    }
}
