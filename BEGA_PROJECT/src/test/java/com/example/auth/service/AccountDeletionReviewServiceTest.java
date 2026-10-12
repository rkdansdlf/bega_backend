package com.example.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.mate.entity.PartyReview;
import com.example.mate.repository.AnonymousPartyReviewAggregateRepository;
import com.example.mate.repository.PartyReviewRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountDeletionReviewServiceTest {

    @Mock
    private PartyReviewRepository partyReviewRepository;

    @Mock
    private AnonymousPartyReviewAggregateRepository aggregateRepository;

    @InjectMocks
    private AccountDeletionReviewService service;

    @Test
    void replacesEveryReviewInvolvingTheDepartingUserWithTargetOnlyRatingTotals() {
        PartyReview authored = PartyReview.builder()
                .id(11L).partyId(100L).reviewerId(7L).revieweeId(8L).rating(4).comment("authored text").build();
        PartyReview received = PartyReview.builder()
                .id(12L).partyId(101L).reviewerId(9L).revieweeId(7L).rating(2).comment("received text").build();
        when(partyReviewRepository.findByReviewerIdOrRevieweeId(7L, 7L)).thenReturn(List.of(authored, received));

        service.anonymize(7L);

        ArgumentCaptor<List<com.example.mate.entity.AnonymousPartyReviewAggregate>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(aggregateRepository).saveAll(captor.capture());
        assertThat(captor.getValue())
                .extracting("revieweeId", "ratingSum", "reviewCount")
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(8L, 4L, 1L),
                        org.assertj.core.groups.Tuple.tuple(7L, 2L, 1L));
        verify(partyReviewRepository).deleteAllInBatch(List.of(authored, received));
    }
}
