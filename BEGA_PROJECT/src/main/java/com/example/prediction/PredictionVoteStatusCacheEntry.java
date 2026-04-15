package com.example.prediction;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PredictionVoteStatusCacheEntry {

    private String gameId;
    private Long homeVotes;
    private Long awayVotes;
    private Long totalVotes;
    private Integer homePercentage;
    private Integer awayPercentage;

    public PredictionVoteStatusCacheEntry(
            String gameId,
            Long homeVotes,
            Long awayVotes,
            Long totalVotes,
            Integer homePercentage,
            Integer awayPercentage
    ) {
        this.gameId = gameId;
        this.homeVotes = homeVotes;
        this.awayVotes = awayVotes;
        this.totalVotes = totalVotes;
        this.homePercentage = homePercentage;
        this.awayPercentage = awayPercentage;
    }

    public static PredictionVoteStatusCacheEntry from(PredictionResponseDto response) {
        if (response == null) {
            return null;
        }
        return new PredictionVoteStatusCacheEntry(
                response.getGameId(),
                response.getHomeVotes(),
                response.getAwayVotes(),
                response.getTotalVotes(),
                response.getHomePercentage(),
                response.getAwayPercentage()
        );
    }

    public PredictionResponseDto toResponseDto() {
        return PredictionResponseDto.builder()
                .gameId(gameId)
                .homeVotes(homeVotes)
                .awayVotes(awayVotes)
                .totalVotes(totalVotes)
                .homePercentage(homePercentage)
                .awayPercentage(awayPercentage)
                .build();
    }
}
