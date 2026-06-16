package com.turtlesoup.ai.dto;

import jakarta.validation.constraints.NotBlank;

public record AskRequest(@NotBlank String question) {
}
