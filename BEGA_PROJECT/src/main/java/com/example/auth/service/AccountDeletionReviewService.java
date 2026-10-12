package com.example.auth.service;

import com.example.mate.entity.AnonymousPartyReviewAggregate;
import com.example.mate.entity.PartyReview;
import com.example.mate.repository.AnonymousPartyReviewAggregateRepository;
import com.example.mate.repository.PartyReviewRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Retains ratings only as anonymous per-reviewee totals and removes individual review records. */
@Service
@RequiredArgsConstructor
public class AccountDeletionReviewService {

    private final PartyReviewRepository partyReviewRepository;
    private final AnonymousPartyReviewAggregateRepository aggregateRepository;

    @Transactional
    public void anonymize(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("A persisted account ID is required for finalization.");
        }
        List<PartyReview> reviews = partyReviewRepository.findByReviewerIdOrRevieweeId(userId, userId);
        if (reviews.isEmpty()) {
            return;
        }

        Map<Long, RatingTotals> totalsByReviewee = new HashMap<>();
        for (PartyReview review : reviews) {
            totalsByReviewee.computeIfAbsent(review.getRevieweeId(), ignored -> new RatingTotals())
                    .add(review.getRating());
        }
        List<AnonymousPartyReviewAggregate> aggregates = new ArrayList<>(totalsByReviewee.size());
        totalsByReviewee.forEach((revieweeId, totals) -> aggregates.add(AnonymousPartyReviewAggregate.builder()
                .revieweeId(revieweeId)
                .ratingSum(totals.sum)
                .reviewCount(totals.count)
                .build()));

        aggregateRepository.saveAll(aggregates);
        partyReviewRepository.deleteAllInBatch(reviews);
    }

    private static final class RatingTotals {
        private long sum;
        private long count;

        private void add(Integer rating) {
            if (rating != null) {
                sum += rating;
                count++;
            }
        }
    }
}
