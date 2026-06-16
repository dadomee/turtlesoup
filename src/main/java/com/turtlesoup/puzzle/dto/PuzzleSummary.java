package com.turtlesoup.puzzle.dto;

import com.turtlesoup.puzzle.Difficulty;
import com.turtlesoup.puzzle.Puzzle;

public record PuzzleSummary(Long id, String title, Difficulty difficulty, String tags) {
    public static PuzzleSummary from(Puzzle p) {
        return new PuzzleSummary(p.getId(), p.getTitle(), p.getDifficulty(), p.getTags());
    }
}
