package com.example.leaderboard.dto;

import com.example.leaderboard.entity.ActivePowerup;
import com.example.leaderboard.entity.UserPowerup;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 활성화된 파워업 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivePowerupDto {
    private Long id;
    private String type;
    private String name;
    private String description;
    private String icon;
    private String gameId;
    private Instant activatedAt;
    private Instant expiresAt;
    private Boolean used;

    public static ActivePowerupDto from(ActivePowerup activePowerup) {
        UserPowerup.PowerupType type = activePowerup.getPowerupType();
        return ActivePowerupDto.builder()
                .id(activePowerup.getId())
                .type(type.name())
                .name(type.getKoreanName())
                .description(type.getDescription())
                .icon(type.getIcon())
                .gameId(activePowerup.getGameId())
                .activatedAt(activePowerup.getActivatedAt())
                .expiresAt(activePowerup.getExpiresAt())
                .used(activePowerup.getUsed())
                .build();
    }
}
