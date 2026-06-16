package com.turtlesoup.ai;

import com.turtlesoup.ai.dto.AskRequest;
import com.turtlesoup.ai.dto.AskResponse;
import com.turtlesoup.puzzle.PuzzleNotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiJudgeService service;

    public AiController(AiJudgeService service) {
        this.service = service;
    }

    @PostMapping("/{puzzleId}/ask")
    public AskResponse ask(@PathVariable Long puzzleId, @Valid @RequestBody AskRequest request) {
        return new AskResponse(service.judge(puzzleId, request.question()));
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(PuzzleNotFoundException.class)
    public Map<String, String> handleNotFound(PuzzleNotFoundException ex) {
        return Map.of("error", ex.getMessage());
    }
}
