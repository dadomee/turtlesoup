package com.turtlesoup.puzzle;

import com.turtlesoup.puzzle.dto.PuzzlePlay;
import com.turtlesoup.puzzle.dto.PuzzleSolution;
import com.turtlesoup.puzzle.dto.PuzzleSummary;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PuzzleService {

    private final PuzzleRepository repository;

    public PuzzleService(PuzzleRepository repository) {
        this.repository = repository;
    }

    public List<PuzzleSummary> list() {
        return repository.findAll().stream().map(PuzzleSummary::from).toList();
    }

    public PuzzlePlay getForPlay(Long id) {
        return PuzzlePlay.from(find(id));
    }

    public PuzzleSolution getSolution(Long id) {
        return PuzzleSolution.from(find(id));
    }

    private Puzzle find(Long id) {
        return repository.findById(id)
            .orElseThrow(() -> new PuzzleNotFoundException(id));
    }
}
