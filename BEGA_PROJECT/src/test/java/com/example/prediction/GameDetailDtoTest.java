package com.example.prediction;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.kbo.entity.GameInningScoreEntity;
import com.example.kbo.entity.GameSummaryEntity;
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
    void shouldIgnorePlaceholderExtraInningRowsWhenNormalizingStatus() {
        GameDetailDto detail = GameDetailDto.from(
                header(
                        "SCHEDULED",
                        LocalDate.now(),
                        LocalTime.MIDNIGHT,
                        null,
                        null
                ),
                List.of(
                        GameInningScoreEntity.builder()
                                .gameId("20260404SSGKIA0")
                                .inning(10)
                                .teamSide("AWAY")
                                .runs(null)
                                .build(),
                        GameInningScoreEntity.builder()
                                .gameId("20260404SSGKIA0")
                                .inning(10)
                                .teamSide("HOME")
                                .runs(null)
                                .build()
                ),
                List.of()
        );

        assertThat(detail.getGameStatus()).isEqualTo("SCHEDULED");
        assertThat(detail.getInningScores()).isEmpty();
    }

    @Test
    void shouldTrimZeroTemplateExtraInningsAfterKnownFinalScore() {
        GameDetailDto detail = GameDetailDto.from(
                header(
                        "COMPLETED",
                        LocalDate.now().minusDays(1),
                        LocalTime.of(14, 0),
                        11,
                        6
                ),
                List.of(
                        GameInningScoreEntity.builder().gameId("20260404SSGKIA0").inning(1).teamSide("AWAY").runs(0).build(),
                        GameInningScoreEntity.builder().gameId("20260404SSGKIA0").inning(1).teamSide("HOME").runs(0).build(),
                        GameInningScoreEntity.builder().gameId("20260404SSGKIA0").inning(2).teamSide("HOME").runs(4).build(),
                        GameInningScoreEntity.builder().gameId("20260404SSGKIA0").inning(3).teamSide("HOME").runs(5).build(),
                        GameInningScoreEntity.builder().gameId("20260404SSGKIA0").inning(4).teamSide("AWAY").runs(2).build(),
                        GameInningScoreEntity.builder().gameId("20260404SSGKIA0").inning(4).teamSide("HOME").runs(1).build(),
                        GameInningScoreEntity.builder().gameId("20260404SSGKIA0").inning(7).teamSide("AWAY").runs(4).build(),
                        GameInningScoreEntity.builder().gameId("20260404SSGKIA0").inning(8).teamSide("HOME").runs(1).build(),
                        GameInningScoreEntity.builder().gameId("20260404SSGKIA0").inning(9).teamSide("AWAY").runs(0).build(),
                        GameInningScoreEntity.builder().gameId("20260404SSGKIA0").inning(9).teamSide("HOME").runs(0).build(),
                        GameInningScoreEntity.builder().gameId("20260404SSGKIA0").inning(10).teamSide("AWAY").runs(0).build(),
                        GameInningScoreEntity.builder().gameId("20260404SSGKIA0").inning(10).teamSide("HOME").runs(0).build(),
                        GameInningScoreEntity.builder().gameId("20260404SSGKIA0").inning(11).teamSide("AWAY").runs(0).build(),
                        GameInningScoreEntity.builder().gameId("20260404SSGKIA0").inning(11).teamSide("HOME").runs(0).build(),
                        GameInningScoreEntity.builder().gameId("20260404SSGKIA0").inning(12).teamSide("AWAY").runs(0).build(),
                        GameInningScoreEntity.builder().gameId("20260404SSGKIA0").inning(12).teamSide("HOME").runs(0).build()
                ),
                List.of()
        );

        assertThat(detail.getInningScores())
                .extracting(GameInningScoreDto::getInning)
                .containsExactly(1, 1, 2, 3, 4, 4, 7, 8, 9, 9);
        assertThat(detail.getInningScores()).extracting(GameInningScoreDto::getInning).doesNotContain(10, 11, 12);
    }

    @Test
    void shouldTrimZeroTemplateExtraInningsAfterDecisiveNinthWhenFinalScoreMissing() {
        GameDetailDto detail = GameDetailDto.from(
                header(
                        "SCHEDULED",
                        LocalDate.now().minusDays(1),
                        LocalTime.of(14, 0),
                        null,
                        null
                ),
                List.of(
                        GameInningScoreEntity.builder().gameId("20260404SSGKIA0").inning(1).teamSide("AWAY").runs(0).build(),
                        GameInningScoreEntity.builder().gameId("20260404SSGKIA0").inning(1).teamSide("HOME").runs(0).build(),
                        GameInningScoreEntity.builder().gameId("20260404SSGKIA0").inning(2).teamSide("HOME").runs(4).build(),
                        GameInningScoreEntity.builder().gameId("20260404SSGKIA0").inning(3).teamSide("HOME").runs(5).build(),
                        GameInningScoreEntity.builder().gameId("20260404SSGKIA0").inning(4).teamSide("AWAY").runs(2).build(),
                        GameInningScoreEntity.builder().gameId("20260404SSGKIA0").inning(4).teamSide("HOME").runs(1).build(),
                        GameInningScoreEntity.builder().gameId("20260404SSGKIA0").inning(7).teamSide("AWAY").runs(4).build(),
                        GameInningScoreEntity.builder().gameId("20260404SSGKIA0").inning(8).teamSide("HOME").runs(1).build(),
                        GameInningScoreEntity.builder().gameId("20260404SSGKIA0").inning(9).teamSide("AWAY").runs(0).build(),
                        GameInningScoreEntity.builder().gameId("20260404SSGKIA0").inning(9).teamSide("HOME").runs(0).build(),
                        GameInningScoreEntity.builder().gameId("20260404SSGKIA0").inning(10).teamSide("AWAY").runs(0).build(),
                        GameInningScoreEntity.builder().gameId("20260404SSGKIA0").inning(10).teamSide("HOME").runs(0).build(),
                        GameInningScoreEntity.builder().gameId("20260404SSGKIA0").inning(11).teamSide("AWAY").runs(0).build(),
                        GameInningScoreEntity.builder().gameId("20260404SSGKIA0").inning(11).teamSide("HOME").runs(0).build(),
                        GameInningScoreEntity.builder().gameId("20260404SSGKIA0").inning(12).teamSide("AWAY").runs(0).build(),
                        GameInningScoreEntity.builder().gameId("20260404SSGKIA0").inning(12).teamSide("HOME").runs(0).build()
                ),
                List.of()
        );

        assertThat(detail.getGameStatus()).isEqualTo("COMPLETED");
        assertThat(detail.getInningScores())
                .extracting(GameInningScoreDto::getInning)
                .containsExactly(1, 1, 2, 3, 4, 4, 7, 8, 9, 9);
        assertThat(detail.getInningScores()).extracting(GameInningScoreDto::getInning).doesNotContain(10, 11, 12);
    }

    @Test
    void shouldNormalizeFalseExtraFlagForRenderedExtraInnings() {
        GameDetailDto detail = GameDetailDto.from(
                header(
                        "COMPLETED",
                        LocalDate.now().minusDays(1),
                        LocalTime.of(14, 0),
                        2,
                        3
                ),
                List.of(
                        GameInningScoreEntity.builder().gameId("20260404SSGKIA0").inning(9).teamSide("AWAY").runs(2).isExtra(false).build(),
                        GameInningScoreEntity.builder().gameId("20260404SSGKIA0").inning(9).teamSide("HOME").runs(2).isExtra(false).build(),
                        GameInningScoreEntity.builder().gameId("20260404SSGKIA0").inning(10).teamSide("AWAY").runs(1).isExtra(false).build(),
                        GameInningScoreEntity.builder().gameId("20260404SSGKIA0").inning(10).teamSide("HOME").runs(0).isExtra(false).build()
                ),
                List.of()
        );

        assertThat(detail.getInningScores())
                .filteredOn(score -> Integer.valueOf(10).equals(score.getInning()))
                .extracting(GameInningScoreDto::getIsExtra)
                .containsExactly(true, true);
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

    @Test
    void shouldHideStructuredInternalSummaryTypes() {
        GameDetailDto detail = GameDetailDto.from(
                header(
                        "COMPLETED",
                        LocalDate.now().minusDays(1),
                        LocalTime.of(14, 0),
                        1,
                        9
                ),
                List.of(),
                List.of(
                        GameSummaryEntity.builder()
                                .gameId("20260419HHLT0")
                                .summaryType("결승타")
                                .playerName("김태연")
                                .detailText("5회말 결승타")
                                .build(),
                        GameSummaryEntity.builder()
                                .gameId("20260419HHLT0")
                                .summaryType("리뷰_WPA")
                                .playerName("기록")
                                .detailText("{\"game_id\":\"20260419HHLT0\"}")
                                .build(),
                        GameSummaryEntity.builder()
                                .gameId("20260419HHLT0")
                                .summaryType(" 프리뷰 ")
                                .playerName("기록")
                                .detailText("{\"game_id\":\"20260419HHLT0\"}")
                                .build()
                )
        );

        assertThat(detail.getSummary())
                .extracting(GameSummaryDto::getType)
                .containsExactly("결승타");
    }

    @Test
    void shouldHideStructuredJsonSummaryDetailsAndBlankRows() {
        GameDetailDto detail = GameDetailDto.from(
                header(
                        "COMPLETED",
                        LocalDate.now().minusDays(1),
                        LocalTime.of(14, 0),
                        1,
                        9
                ),
                List.of(),
                List.of(
                        GameSummaryEntity.builder()
                                .gameId("20260419HHLT0")
                                .summaryType("기타")
                                .playerName("기록")
                                .detailText("[{\"inning\":\"5회말\"}]")
                                .build(),
                        GameSummaryEntity.builder()
                                .gameId("20260419HHLT0")
                                .summaryType(null)
                                .playerName("기록")
                                .detailText("타자 기록")
                                .build(),
                        GameSummaryEntity.builder()
                                .gameId("20260419HHLT0")
                                .summaryType("홈런")
                                .playerName("레이예스")
                                .detailText("3회말 우월 홈런")
                                .build()
                )
        );

        assertThat(detail.getSummary())
                .extracting(GameSummaryDto::getType)
                .containsExactly("홈런");
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
