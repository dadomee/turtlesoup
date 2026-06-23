package com.turtlesoup.room;

import com.turtlesoup.puzzle.Difficulty;
import com.turtlesoup.puzzle.Puzzle;
import com.turtlesoup.puzzle.PuzzleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    @Mock
    PuzzleRepository puzzles;

    RoomService service;

    @BeforeEach
    void setup() {
        service = new RoomService(puzzles);
    }

    @Test
    void createReturnsRoomWithCode() {
        Room room = service.create("호스트");
        assertThat(room.getCode()).hasSize(4);
        assertThat(room.getHostName()).isEqualTo("호스트");
        assertThat(room.hasPuzzle()).isFalse();
        assertThat(service.get(room.getCode())).isSameAs(room);
    }

    @Test
    void setPuzzleStoresPuzzle() {
        Room room = service.create("호스트");
        room.setPuzzle("제목", "상황", "정답");
        assertThat(room.hasPuzzle()).isTrue();
        assertThat(room.getScenario()).isEqualTo("상황");
        assertThat(room.getSolution()).isEqualTo("정답");
    }

    @Test
    void codesAreUnique() {
        java.util.Set<String> codes = new java.util.HashSet<>();
        for (int i = 0; i < 200; i++) codes.add(service.create("h").getCode());
        assertThat(codes).hasSize(200);
    }

    @Test
    void getThrowsWhenMissing() {
        assertThatThrownBy(() -> service.get("ZZZZ")).isInstanceOf(RoomNotFoundException.class);
    }

    @Test
    void tracksParticipants() {
        Room room = service.create("호스트");
        String code = room.getCode();
        service.join(code, "철수");
        service.join(code, "영희");
        assertThat(service.get(code).participants()).containsExactly("철수", "영희");
        service.leave(code, "철수");
        assertThat(service.get(code).participants()).containsExactly("영희");
    }

    @Test
    void hintsUsedCapsAtThree() {
        Room room = service.create("호스트");
        assertThat(room.canHint()).isTrue();
        assertThat(room.useHint()).isEqualTo(1);
        room.useHint();
        room.useHint();
        assertThat(room.canHint()).isFalse();
        assertThat(room.getHintsUsed()).isEqualTo(3);
    }

    @Test
    void removeDeletesRoom() {
        Room room = service.create("호스트");
        service.remove(room.getCode());
        assertThatThrownBy(() -> service.get(room.getCode())).isInstanceOf(RoomNotFoundException.class);
    }

    @Test
    void aiHostedRoomGetsRandomPuzzle() {
        when(puzzles.findAll()).thenReturn(java.util.List.of(
            new Puzzle("바다거북 스프", "상황", "정답", Difficulty.HARD, "고전", "h1", "h2", "h3")));
        Room room = service.create("호스트", true);
        assertThat(room.isAiHosted()).isTrue();
        assertThat(room.hasPuzzle()).isTrue();
        assertThat(room.getScenario()).isEqualTo("상황");
    }
}
