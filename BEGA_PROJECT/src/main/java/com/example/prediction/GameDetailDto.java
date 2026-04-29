package com.example.prediction;

import com.example.kbo.entity.GameEntity;
import com.example.kbo.entity.GameInningScoreEntity;
import com.example.kbo.entity.GameMetadataEntity;
import com.example.kbo.entity.GameSummaryEntity;
import com.example.kbo.repository.GameDetailHeaderProjection;
import com.example.kbo.util.GameStatusResolver;
import com.example.kbo.util.GameSummaryDisplayPolicy;
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
        List<GameInningScoreEntity> meaningfulInningScores = GameInningScoreSupport.normalizeMeaningful(
                inningScores,
                game.getHomeScore(),
                game.getAwayScore()
        );

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
                .gameStatus(GameStatusResolver.resolveEffectiveStatus(
                        game.getGameStatus(),
                        game.getGameDate(),
                        metadata != null ? metadata.getStartTime() : null,
                        game.getHomeScore(),
                        game.getAwayScore(),
                        (game.getHomeScore() != null && game.getAwayScore() != null)
                                || !meaningfulInningScores.isEmpty()
                ))
                .inningScores(mapInningScores(meaningfulInningScores))
                .summary(mapSummaries(summaries))
                .build();
    }

    public static GameDetailDto from(
            GameDetailHeaderProjection header,
            List<GameInningScoreEntity> inningScores,
            List<GameSummaryEntity> summaries
    ) {
        List<GameInningScoreEntity> meaningfulInningScores = GameInningScoreSupport.normalizeMeaningful(
                inningScores,
                header.getHomeScore(),
                header.getAwayScore()
        );

        return GameDetailDto.builder()
                .gameId(header.getGameId())
                .gameDate(header.getGameDate())
                .stadium(header.getStadium())
                .stadiumName(header.getStadiumName())
                .startTime(header.getStartTime())
                .attendance(header.getAttendance())
                .weather(header.getWeather())
                .gameTimeMinutes(header.getGameTimeMinutes())
                .homeTeam(header.getHomeTeam())
                .awayTeam(header.getAwayTeam())
                .homeScore(header.getHomeScore())
                .awayScore(header.getAwayScore())
                .homePitcher(header.getHomePitcher())
                .awayPitcher(header.getAwayPitcher())
                .gameStatus(GameStatusResolver.resolveEffectiveStatus(
                        header.getGameStatus(),
                        header.getGameDate(),
                        header.getStartTime(),
                        header.getHomeScore(),
                        header.getAwayScore(),
                        (header.getHomeScore() != null && header.getAwayScore() != null)
                                || !meaningfulInningScores.isEmpty()
                ))
                .inningScores(mapInningScores(meaningfulInningScores))
                .summary(mapSummaries(summaries))
                .build();
    }

    private static List<GameInningScoreDto> mapInningScores(List<GameInningScoreEntity> inningScores) {
        if (inningScores == null || inningScores.isEmpty()) {
            return List.of();
        }

        return inningScores.stream()
                .map(GameInningScoreDto::fromEntity)
                .collect(Collectors.toList());
    }

    private static List<GameSummaryDto> mapSummaries(List<GameSummaryEntity> summaries) {
        if (summaries == null || summaries.isEmpty()) {
            return List.of();
        }

        return summaries.stream()
                .filter(GameSummaryDisplayPolicy::isDisplayable)
                .map(GameSummaryDto::fromEntity)
                .collect(Collectors.toList());
    }
}
