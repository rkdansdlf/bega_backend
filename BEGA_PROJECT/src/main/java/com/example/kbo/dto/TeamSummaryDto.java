package com.example.kbo.dto;

import com.example.kbo.entity.TeamEntity;

public record TeamSummaryDto(
        String teamId,
        String teamName,
        String teamShortName,
        Boolean isActive
) {
    public static TeamSummaryDto from(TeamEntity team) {
        return new TeamSummaryDto(
                team.getTeamId(),
                team.getTeamName(),
                team.getTeamShortName(),
                team.getIsActive()
        );
    }
}
