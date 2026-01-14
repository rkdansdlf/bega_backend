package com.example.prediction;

import com.example.demo.entity.GameEntity;
import java.time.LocalDate;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(toBuilder = true)
public class MatchDto {
    private String gameId;
    private LocalDate gameDate;
    private String homeTeam;
    private String awayTeam;
    private String stadium;
    private Integer homeScore;
    private Integer awayScore;
    private String winner;
    private Boolean isDummy;

    // New Fields
    private PitcherDto homePitcher;
    private PitcherDto awayPitcher;
    private String aiSummary;
    private WinProbabilityDto winProbability;

    @Getter
    @Builder
    public static class PitcherDto {
        private String name;
        private String era;
        private Integer win;
        private Integer loss;
        private String imgUrl;
    }

    @Getter
    @Builder
    public static class WinProbabilityDto {
        private Double home;
        private Double away;
    }

    public static MatchDto fromEntity(GameEntity game) {
        LocalDate displayDate = game.getGameDate();

        // 더미 데이터면 항상 내일 날짜로 표시
        if (Boolean.TRUE.equals(game.getIsDummy())) {
            displayDate = LocalDate.now().plusDays(1);
        }

        // Construct DTO
        return MatchDto.builder()
                .gameId(game.getGameId())
                .gameDate(displayDate)
                .homeTeam(game.getHomeTeam())
                .awayTeam(game.getAwayTeam())
                .stadium(game.getStadium())
                .homeScore(game.getHomeScore())
                .awayScore(game.getAwayScore())
                .winner(game.getWinner())
                .isDummy(game.getIsDummy())
                // Map new fields from Entity (현재 DB에 확장 필드가 없으므로 null로 설정)
                .homePitcher(game.getHomePitcher() != null ? PitcherDto.builder()
                        .name(game.getHomePitcher()) // DB에는 pitcher 이름만 있음
                        .era(null)
                        .win(null)
                        .loss(null)
                        .imgUrl(null)
                        .build() : null)
                .awayPitcher(game.getAwayPitcher() != null ? PitcherDto.builder()
                        .name(game.getAwayPitcher()) // DB에는 pitcher 이름만 있음
                        .era(null)
                        .win(null)
                        .loss(null)
                        .imgUrl(null)
                        .build() : null)
                .aiSummary(null) // DB에 없는 필드
                .winProbability(null) // DB에 없는 필드
                .build();
    }
}
