package com.example.mate.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.mate.repository.PartyReviewRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PartyReviewRatingServiceTest {

    @Mock
    private PartyReviewRepository partyReviewRepository;

    @InjectMocks
    private PartyReviewRatingService service;

    @Test
    void mapsCombinedIndividualAndAnonymousTotalsWithoutReviewerDetails() {
        PartyReviewRepository.RevieweeRatingTotals combined = individualTotals(8L, 15L, 4L);
        when(partyReviewRepository.findCombinedRatingTotalsByRevieweeIds(List.of(8L))).thenReturn(List.of(combined));

        PartyReviewRatingService.RatingSummary result = service.findRatingSummariesByRevieweeIds(List.of(8L)).getFirst();

        assertThat(result.revieweeId()).isEqualTo(8L);
        assertThat(result.reviewCount()).isEqualTo(4L);
        assertThat(result.averageRating()).isEqualTo(3.75);
    }

    private PartyReviewRepository.RevieweeRatingTotals individualTotals(Long id, Long sum, Long count) {
        PartyReviewRepository.RevieweeRatingTotals projection = org.mockito.Mockito.mock(
                PartyReviewRepository.RevieweeRatingTotals.class);
        when(projection.getRevieweeId()).thenReturn(id);
        when(projection.getRatingSum()).thenReturn(sum);
        when(projection.getReviewCount()).thenReturn(count);
        return projection;
    }

}
