package com.example.mate.service;

import com.example.mate.entity.Party;
import com.example.mate.entity.PartyApplication;
import com.example.mate.repository.PartyApplicationRepository;
import com.example.mate.repository.PartyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PartyLifecycleMutationServiceTest {

    @Mock
    private PartyRepository partyRepository;

    @Mock
    private PartyApplicationRepository applicationRepository;

    @InjectMocks
    private PartyLifecycleMutationService service;

    @Test
    void rejectExpiredApplication_locksPartyBeforeApplicationAndClaimsState() {
        Instant now = Instant.now();
        Party party = Party.builder().id(10L).build();
        PartyApplication application = PartyApplication.builder()
                .id(20L)
                .partyId(10L)
                .applicantId(30L)
                .isApproved(false)
                .isRejected(false)
                .isPaid(true)
                .responseDeadline(now.minusSeconds(1))
                .build();

        when(partyRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(party));
        when(applicationRepository.findByIdAndApplicantIdForUpdate(20L, 30L))
                .thenReturn(Optional.of(application));
        when(applicationRepository.save(application)).thenReturn(application);

        PartyLifecycleMutationService.ExpiredApplicationMutation result =
                service.rejectExpiredApplication(10L, 20L, 30L, now);

        assertThat(result).isNotNull();
        assertThat(result.application().getIsRejected()).isTrue();
        assertThat(result.application().getRejectedAt()).isEqualTo(now);
        InOrder order = inOrder(partyRepository, applicationRepository);
        order.verify(partyRepository).findByIdForUpdate(10L);
        order.verify(applicationRepository).findByIdAndApplicantIdForUpdate(20L, 30L);
    }

    @Test
    void markApplicationUnpaidAfterRefund_preservesPartyFirstLockOrder() {
        PartyApplication application = PartyApplication.builder()
                .id(20L)
                .partyId(10L)
                .applicantId(30L)
                .isRejected(true)
                .isPaid(true)
                .build();

        when(partyRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(Party.builder().id(10L).build()));
        when(applicationRepository.findByIdAndApplicantIdForUpdate(20L, 30L))
                .thenReturn(Optional.of(application));

        service.markApplicationUnpaidAfterRefund(10L, 20L, 30L);

        assertThat(application.getIsPaid()).isFalse();
        verify(applicationRepository).save(application);
        InOrder order = inOrder(partyRepository, applicationRepository);
        order.verify(partyRepository).findByIdForUpdate(10L);
        order.verify(applicationRepository).findByIdAndApplicantIdForUpdate(20L, 30L);
    }
}
