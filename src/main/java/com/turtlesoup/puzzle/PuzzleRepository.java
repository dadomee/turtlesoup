package com.turtlesoup.puzzle;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PuzzleRepository extends JpaRepository<Puzzle, Long> {
}
