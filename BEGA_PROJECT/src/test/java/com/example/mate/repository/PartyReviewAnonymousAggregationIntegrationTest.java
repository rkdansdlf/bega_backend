package com.example.mate.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.auth.service.AccountDeletionReviewService;
import com.example.mate.entity.AnonymousPartyReviewAggregate;
import com.example.mate.entity.PartyReview;
import com.example.mate.service.PartyReviewRatingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import({AccountDeletionReviewService.class, PartyReviewRatingService.class})
class PartyReviewAnonymousAggregationIntegrationTest {

    @Autowired
    private PartyReviewRepository partyReviewRepository;

    @Autowired
    private AnonymousPartyReviewAggregateRepository aggregateRepository;

    @Autowired
    private AccountDeletionReviewService accountDeletionReviewService;

    @Autowired
    private PartyReviewRatingService partyReviewRatingService;

    @Test
    void accountDeletionStoresOnlyTargetRatingTotalsAndRatingQueriesIncludeThem() {
        partyReviewRepository.save(PartyReview.builder()
                .partyId(100L).reviewerId(7L).revieweeId(8L).rating(5).comment("private text").build());
        partyReviewRepository.save(PartyReview.builder()
                .partyId(101L).reviewerId(9L).revieweeId(7L).rating(3).comment("private feedback").build());
        partyReviewRepository.flush();

        accountDeletionReviewService.anonymize(7L);
        partyReviewRepository.flush();

        assertThat(partyReviewRepository.findByReviewerIdOrRevieweeId(7L, 7L)).isEmpty();
        assertThat(aggregateRepository.findAll())
                .extracting(AnonymousPartyReviewAggregate::getRevieweeId)
                .containsExactlyInAnyOrder(7L, 8L);
        assertThat(partyReviewRatingService.findRatingSummariesByRevieweeIds(java.util.List.of(7L, 8L)))
                .extracting(PartyReviewRatingService.RatingSummary::revieweeId)
                .containsExactlyInAnyOrder(7L, 8L);
        assertThat(partyReviewRatingService.calculateAverageRating(7L)).isEqualTo(3.0);
        assertThat(partyReviewRatingService.calculateAverageRating(8L)).isEqualTo(5.0);
    }
}
