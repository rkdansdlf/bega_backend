package com.example.prediction;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.kbo.entity.GameInningScoreEntity;
import com.example.kbo.repository.GameDetailHeaderProjection;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class GameDetailDtoTest {

    @Test
    void shouldNormalizeScheduledPastGameWithScoreAsCompleted() {
        GameDetailDto detail = GameDetailDto.from(
                header(
                        "SCHEDULED",
                        LocalDate.now().minusDays(1),
                        LocalTime.of(14, 0),
                        6,
                        3
                ),
                List.of(),
                List.of()
        );

        assertThat(detail.getGameStatus()).isEqualTo("COMPLETED");
    }

    @Test
    void shouldNormalizeScheduledStartedGameWithInningDataAsLive() {
        GameDetailDto detail = GameDetailDto.from(
                header(
                        "SCHEDULED",
                        LocalDate.now(),
                        LocalTime.MIDNIGHT,
                        null,
                        null
                ),
                List.of(GameInningScoreEntity.builder()
                        .gameId("20260404SSGKIA0")
                        .inning(1)
                        .teamSide("AWAY")
                        .runs(1)
                        .build()),
                List.of()
        );

        assertThat(detail.getGameStatus()).isEqualTo("LIVE");
    }

    @Test
    void shouldNormalizeCompletedTieGameAsDraw() {
        GameDetailDto detail = GameDetailDto.from(
                header(
                        "FINAL",
                        LocalDate.now().minusDays(1),
                        LocalTime.of(14, 0),
                        4,
                        4
                ),
                List.of(),
                List.of()
        );

        assertThat(detail.getGameStatus()).isEqualTo("DRAW");
    }

    private GameDetailHeaderProjection header(
            String gameStatus,
            LocalDate gameDate,
            LocalTime startTime,
            Integer homeScore,
            Integer awayScore
    ) {
        return new GameDetailHeaderProjection() {
            @Override
            public String getGameId() {
                return "20260404SSGKIA0";
            }

            @Override
            public LocalDate getGameDate() {
                return gameDate;
            }

            @Override
            public String getStadium() {
                return "문학";
            }

            @Override
            public String getStadiumName() {
                return "인천 SSG 랜더스필드";
            }

            @Override
            public LocalTime getStartTime() {
                return startTime;
            }

            @Override
            public Integer getAttendance() {
                return null;
            }

            @Override
            public String getWeather() {
                return null;
            }

            @Override
            public Integer getGameTimeMinutes() {
                return null;
            }

            @Override
            public String getHomeTeam() {
                return "SSG";
            }

            @Override
            public String getAwayTeam() {
                return "KIA";
            }

            @Override
            public Integer getHomeScore() {
                return homeScore;
            }

            @Override
            public Integer getAwayScore() {
                return awayScore;
            }

            @Override
            public String getHomePitcher() {
                return null;
            }

            @Override
            public String getAwayPitcher() {
                return null;
            }

            @Override
            public String getGameStatus() {
                return gameStatus;
            }
        };
    }
}
