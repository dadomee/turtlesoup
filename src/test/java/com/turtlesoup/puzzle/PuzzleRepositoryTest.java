package com.turtlesoup.puzzle;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class PuzzleRepositoryTest {

    @Autowired
    PuzzleRepository repository;

    @Test
    void savesAndFindsPuzzle() {
        Puzzle saved = repository.save(
            new Puzzle("제목", "상황", "정답", Difficulty.EASY, "고전"));

        assertThat(repository.findById(saved.getId())).isPresent();
        assertThat(repository.findById(saved.getId()).get().getScenario())
            .isEqualTo("상황");
    }
}
