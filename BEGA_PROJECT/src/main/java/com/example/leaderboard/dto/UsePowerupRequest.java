package com.example.leaderboard.dto;

import jakarta.validation.constraints.NotBlank;

public record UsePowerupRequest(
        @NotBlank(message = "경기 ID가 필요합니다.") String gameId) {
}
