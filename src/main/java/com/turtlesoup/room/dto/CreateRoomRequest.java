package com.turtlesoup.room.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateRoomRequest(
    @NotBlank String hostName,
    Long puzzleId,
    String title,
    String scenario,
    String solution
) {}
