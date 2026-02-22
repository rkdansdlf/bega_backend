package com.example.prediction;

import com.example.kbo.entity.GameEntity;
import com.example.kbo.entity.GameInningScoreEntity;
import com.example.kbo.entity.GameMetadataEntity;
import com.example.kbo.entity.GameSummaryEntity;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Builder
public class GameDetailDto {
    private String gameId;
    private LocalDate gameDate;
    private String stadium;
    private String stadiumName;
    private LocalTime startTime;
    private Integer attendance;
    private String weather;
    private Integer gameTimeMinutes;
    private String homeTeam;
    private String awayTeam;
    private Integer homeScore;
    private Integer awayScore;
    private String homePitcher;
    private String awayPitcher;
    private String gameStatus;
    private List<GameInningScoreDto> inningScores;
    private List<GameSummaryDto> summary;

    public static GameDetailDto from(
            GameEntity game,
            GameMetadataEntity metadata,
            List<GameInningScoreEntity> inningScores,
            List<GameSummaryEntity> summaries
    ) {
        return GameDetailDto.builder()
                .gameId(game.getGameId())
                .gameDate(game.getGameDate())
                .stadium(game.getStadium())
                .stadiumName(metadata != null ? metadata.getStadiumName() : null)
                .startTime(metadata != null ? metadata.getStartTime() : null)
                .attendance(metadata != null ? metadata.getAttendance() : null)
                .weather(metadata != null ? metadata.getWeather() : null)
                .gameTimeMinutes(metadata != null ? metadata.getGameTimeMinutes() : null)
                .homeTeam(game.getHomeTeam())
                .awayTeam(game.getAwayTeam())
                .homeScore(game.getHomeScore())
                .awayScore(game.getAwayScore())
                .homePitcher(game.getHomePitcher())
                .awayPitcher(game.getAwayPitcher())
                .gameStatus(game.getGameStatus())
                .inningScores(inningScores.stream()
                        .map(GameInningScoreDto::fromEntity)
                        .collect(Collectors.toList()))
                .summary(summaries.stream()
                        .map(GameSummaryDto::fromEntity)
                        .collect(Collectors.toList()))
                .build();
    }
}
