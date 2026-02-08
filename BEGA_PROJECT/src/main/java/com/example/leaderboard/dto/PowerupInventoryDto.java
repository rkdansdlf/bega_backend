package com.example.leaderboard.dto;

import com.example.leaderboard.entity.UserPowerup;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 파워업 인벤토리 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PowerupInventoryDto {
    private String type;
    private String name;
    private String description;
    private String icon;
    private Integer quantity;
    private Double multiplier;

    public static PowerupInventoryDto from(UserPowerup powerup) {
        UserPowerup.PowerupType type = powerup.getPowerupType();
        return PowerupInventoryDto.builder()
                .type(type.name())
                .name(type.getKoreanName())
                .description(type.getDescription())
                .icon(type.getIcon())
                .quantity(powerup.getQuantity())
                .multiplier(type.getMultiplier())
                .build();
    }

    public static PowerupInventoryDto fromType(UserPowerup.PowerupType type, int quantity) {
        return PowerupInventoryDto.builder()
                .type(type.name())
                .name(type.getKoreanName())
                .description(type.getDescription())
                .icon(type.getIcon())
                .quantity(quantity)
                .multiplier(type.getMultiplier())
                .build();
    }
}
