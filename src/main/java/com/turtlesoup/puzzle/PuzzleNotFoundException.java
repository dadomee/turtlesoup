package com.turtlesoup.puzzle;

public class PuzzleNotFoundException extends RuntimeException {
    public PuzzleNotFoundException(Long id) {
        super("문제를 찾을 수 없습니다: " + id);
    }
}
