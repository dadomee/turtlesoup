package com.turtlesoup.history;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class PlayHistoryRepositoryTest {

    @Autowired
    PlayHistoryRepository repository;

    @Test
    void savesPlayHistory() {
        PlayHistory saved = repository.save(
            new PlayHistory(1L, GameMode.AI, "다솜", 5, true));

        PlayHistory found = repository.findById(saved.getId()).orElseThrow();
        assertThat(found.getNickname()).isEqualTo("다솜");
        assertThat(found.getQuestionCount()).isEqualTo(5);
        assertThat(found.isSolved()).isTrue();
        assertThat(found.getCreatedAt()).isNotNull();
    }
}
