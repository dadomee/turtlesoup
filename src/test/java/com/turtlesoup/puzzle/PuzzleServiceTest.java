package com.turtlesoup.puzzle;

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
}
