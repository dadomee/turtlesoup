package com.turtlesoup.ai;

import com.turtlesoup.ai.dto.AskRequest;
import com.turtlesoup.ai.dto.AskResponse;
import com.turtlesoup.ai.dto.HintResponse;
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

    @PostMapping("/{puzzleId}/hint/{n}")
    public HintResponse hint(@PathVariable Long puzzleId, @PathVariable int n) {
        return new HintResponse(n, service.hint(puzzleId, n));
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(PuzzleNotFoundException.class)
    public Map<String, String> handleNotFound(PuzzleNotFoundException ex) {
        return Map.of("error", ex.getMessage());
    }

    // 레이트리밋: 상황별(분당/일일) 귀여운 문구를 429 본문으로 내려 프런트가 그대로 보여준다.
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    @ExceptionHandler(AiBusyException.class)
    public Map<String, String> handleBusy(AiBusyException ex) {
        return Map.of("error", ex.getMessage(), "scope", ex.getScope().name());
    }
}
