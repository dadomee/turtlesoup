package com.turtlesoup.puzzle.dto;

import com.turtlesoup.puzzle.Puzzle;

public record PuzzleSolution(Long id, String solution) {
    public static PuzzleSolution from(Puzzle p) {
        return new PuzzleSolution(p.getId(), p.getSolution());
    }
}
