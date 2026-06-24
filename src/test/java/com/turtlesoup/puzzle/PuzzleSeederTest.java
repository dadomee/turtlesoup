package com.turtlesoup.puzzle;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PuzzleSeederTest {

    @Autowired
    PuzzleRepository repository;

    @Test
    void seedsClassicPuzzlesOnStartup() {
        assertThat(repository.count()).isEqualTo(37);
        assertThat(repository.findAll())
            .anyMatch(p -> p.getTitle().contains("바다거북"));
    }

    @Test
    void seededPuzzlesHaveThreeHints() {
        Puzzle p = repository.findAll().stream()
            .filter(x -> x.getTitle().contains("바다거북")).findFirst().orElseThrow();
        assertThat(p.getHint(1)).isNotBlank();
        assertThat(p.getHint(2)).isNotBlank();
        assertThat(p.getHint(3)).isNotBlank();
    }
}
