package com.example.mate.service;

import com.example.mate.repository.PartyReviewRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PartyReviewRatingService {

    private final PartyReviewRepository partyReviewRepository;

    @Transactional(readOnly = true)
    public List<RatingSummary> findRatingSummariesByRevieweeIds(Collection<Long> revieweeIds) {
        if (revieweeIds == null || revieweeIds.isEmpty()) {
            return List.of();
        }

        Map<Long, Totals> totalsByReviewee = new HashMap<>();
        partyReviewRepository.findCombinedRatingTotalsByRevieweeIds(revieweeIds).forEach(row ->
                totalsByReviewee.put(row.getRevieweeId(), new Totals(row.getRatingSum(), row.getReviewCount())));

        List<RatingSummary> summaries = new ArrayList<>(totalsByReviewee.size());
        totalsByReviewee.forEach((revieweeId, totals) -> summaries.add(new RatingSummary(
                revieweeId,
                totals.count() == 0 ? null : (double) totals.sum() / totals.count(),
                totals.count())));
        return summaries;
    }

    @Transactional(readOnly = true)
    public Double calculateAverageRating(Long revieweeId) {
        if (revieweeId == null) {
            return null;
        }
        return findRatingSummariesByRevieweeIds(List.of(revieweeId)).stream()
                .map(RatingSummary::averageRating)
                .findFirst()
                .orElse(null);
    }

    public record RatingSummary(Long revieweeId, Double averageRating, Long reviewCount) {
    }

    private record Totals(long sum, long count) {
    }
}
