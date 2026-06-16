package com.turtlesoup.puzzle.dto;

import com.turtlesoup.puzzle.Difficulty;
import com.turtlesoup.puzzle.Puzzle;

public record PuzzlePlay(Long id, String title, String scenario, Difficulty difficulty, String tags) {
    public static PuzzlePlay from(Puzzle p) {
        return new PuzzlePlay(p.getId(), p.getTitle(), p.getScenario(), p.getDifficulty(), p.getTags());
    }
}
