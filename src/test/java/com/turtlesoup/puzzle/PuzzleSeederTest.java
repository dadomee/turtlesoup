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
        assertThat(repository.count()).isEqualTo(6);
        assertThat(repository.findAll())
            .anyMatch(p -> p.getTitle().contains("바다거북"));
    }
}
