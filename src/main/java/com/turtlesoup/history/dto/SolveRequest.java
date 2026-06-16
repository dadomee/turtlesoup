package com.turtlesoup.history.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record SolveRequest(@NotBlank String nickname, @Min(0) int questionCount) {
}
