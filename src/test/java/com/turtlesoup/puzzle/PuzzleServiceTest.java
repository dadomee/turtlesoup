package com.turtlesoup.puzzle;

import com.turtlesoup.puzzle.dto.PuzzleHint;
import com.turtlesoup.puzzle.dto.PuzzlePlay;
import com.turtlesoup.puzzle.dto.PuzzleSolution;
import com.turtlesoup.puzzle.dto.PuzzleSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PuzzleServiceTest {

    @Mock
    PuzzleRepository repository;

    @InjectMocks
    PuzzleService service;

    private Puzzle samplePuzzle() {
        return new Puzzle("바다거북 스프", "상황 텍스트", "정답 텍스트", Difficulty.HARD, "고전");
    }

    @Test
    void listReturnsSummariesWithoutSolution() {
        when(repository.findAll()).thenReturn(List.of(samplePuzzle()));

        List<PuzzleSummary> result = service.list();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).title()).isEqualTo("바다거북 스프");
    }

    @Test
    void getForPlayReturnsScenarioButNoSolution() {
        when(repository.findById(1L)).thenReturn(Optional.of(samplePuzzle()));

        PuzzlePlay result = service.getForPlay(1L);

        assertThat(result.scenario()).isEqualTo("상황 텍스트");
    }

    @Test
    void getForPlayThrowsWhenMissing() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getForPlay(99L))
            .isInstanceOf(PuzzleNotFoundException.class);
    }

    @Test
    void getSolutionReturnsSolution() {
        when(repository.findById(1L)).thenReturn(Optional.of(samplePuzzle()));

        PuzzleSolution result = service.getSolution(1L);

        assertThat(result.solution()).isEqualTo("정답 텍스트");
    }

    @Test
    void getHintReturnsNthHint() {
        Puzzle p = new Puzzle("t", "s", "sol", Difficulty.EASY, "고전", "힌트1", "힌트2", "힌트3");
        when(repository.findById(1L)).thenReturn(java.util.Optional.of(p));
        assertThat(service.getHint(1L, 2).hint()).isEqualTo("힌트2");
    }

    @Test
    void getHintThrowsWhenMissing() {
        when(repository.findById(9L)).thenReturn(java.util.Optional.empty());
        assertThatThrownBy(() -> service.getHint(9L, 1)).isInstanceOf(PuzzleNotFoundException.class);
    }
}
