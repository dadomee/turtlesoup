package com.turtlesoup.history;

import com.turtlesoup.history.dto.SolveRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
public class HistoryController {

    private final PlayHistoryService service;

    public HistoryController(PlayHistoryService service) {
        this.service = service;
    }

    @PostMapping("/{puzzleId}/solve")
    public void solve(@PathVariable Long puzzleId, @Valid @RequestBody SolveRequest request) {
        service.recordSolve(puzzleId, GameMode.AI, request.nickname(), request.questionCount());
    }
}
