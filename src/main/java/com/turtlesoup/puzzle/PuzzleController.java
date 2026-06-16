package com.turtlesoup.puzzle;

import com.turtlesoup.puzzle.dto.PuzzlePlay;
import com.turtlesoup.puzzle.dto.PuzzleSolution;
import com.turtlesoup.puzzle.dto.PuzzleSummary;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/puzzles")
public class PuzzleController {

    private final PuzzleService service;

    public PuzzleController(PuzzleService service) {
        this.service = service;
    }

    @GetMapping
    public List<PuzzleSummary> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public PuzzlePlay play(@PathVariable Long id) {
        return service.getForPlay(id);
    }

    @GetMapping("/{id}/solution")
    public PuzzleSolution solution(@PathVariable Long id) {
        return service.getSolution(id);
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(PuzzleNotFoundException.class)
    public void handleNotFound() {
    }
}
