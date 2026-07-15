package com.example.mate.scheduler;

import com.example.mate.dto.PartyApplicationDTO;
import com.example.mate.entity.Party;
import com.example.mate.entity.PartyApplication;
import com.example.mate.entity.PaymentStatus;
import com.example.mate.repository.PartyApplicationRepository;
import com.example.mate.repository.PartyRepository;
import com.example.mate.service.PartyLifecycleMutationService;
import com.example.mate.service.PaymentTransactionService;
import com.example.notification.entity.Notification;
import com.example.notification.service.NotificationService;
import org.jobrunr.scheduling.JobScheduler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PartyLifecycleSchedulerTest {

    @Mock
    private PartyRepository partyRepository;

    @Mock
    private PartyApplicationRepository applicationRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private JobScheduler jobScheduler;

    @Mock
    private PaymentTransactionService paymentTransactionService;

    @Mock
    private PartyLifecycleMutationService lifecycleMutationService;

    @InjectMocks
    private PartyLifecycleScheduler scheduler;

    @Test
    @DisplayName("내일 경기 알림은 파티 신청자를 한 번의 bulk 쿼리로 조회한다 (N+1 방지)")
    void sendGameTomorrowReminders_loadsApplicantsInOneBulkQuery() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        Party matched1 = newParty(101L, 1L, tomorrow);
        Party matched2 = newParty(102L, 2L, tomorrow);
        Party checkedIn1 = newParty(201L, 3L, tomorrow);

        when(partyRepository.findByStatusAndGameDate(Party.PartyStatus.MATCHED, tomorrow))
                .thenReturn(List.of(matched1, matched2));
        when(partyRepository.findByStatusAndGameDate(Party.PartyStatus.CHECKED_IN, tomorrow))
                .thenReturn(List.of(checkedIn1));

        when(applicationRepository.findByPartyIdInAndIsApprovedTrue(any()))
                .thenReturn(List.of(
                        newApplication(101L, 1001L),
                        newApplication(101L, 1002L),
                        newApplication(102L, 1003L),
                        newApplication(201L, 1004L)
                ));

        scheduler.sendGameTomorrowReminders();

        verify(applicationRepository, times(1)).findByPartyIdInAndIsApprovedTrue(any());
        verify(applicationRepository, never()).findByPartyIdAndIsApprovedTrue(anyLong());

        // 호스트 3명 + 신청자 4명 = 7회 알림
        verify(notificationService, times(7)).createNotification(
                anyLong(),
                eq(Notification.NotificationType.GAME_TOMORROW_REMINDER),
                anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("대상 파티가 없으면 신청자 bulk 쿼리도 호출하지 않는다")
    void sendGameDayReminders_skipsBulkQueryWhenNoParties() {
        LocalDate today = LocalDate.now();

        when(partyRepository.findByStatusAndGameDate(Party.PartyStatus.MATCHED, today))
                .thenReturn(Collections.emptyList());
        when(partyRepository.findByStatusAndGameDate(Party.PartyStatus.CHECKED_IN, today))
                .thenReturn(Collections.emptyList());

        scheduler.sendGameDayReminders();

        verify(applicationRepository, never()).findByPartyIdInAndIsApprovedTrue(any());
        verify(applicationRepository, never()).findByPartyIdAndIsApprovedTrue(anyLong());
        verify(notificationService, never()).createNotification(
                anyLong(), any(), anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("승인된 신청자 bulk 결과는 partyId 별로 정확히 그룹화되어 알림이 발송된다")
    void sendGameTomorrowReminders_groupsApplicantsByParty() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        Party party = newParty(500L, 7L, tomorrow);

        when(partyRepository.findByStatusAndGameDate(Party.PartyStatus.MATCHED, tomorrow))
                .thenReturn(List.of(party));
        when(partyRepository.findByStatusAndGameDate(Party.PartyStatus.CHECKED_IN, tomorrow))
                .thenReturn(Collections.emptyList());

        when(applicationRepository.findByPartyIdInAndIsApprovedTrue(any()))
                .thenReturn(List.of(
                        newApplication(500L, 9001L),
                        newApplication(500L, 9002L),
                        newApplication(500L, 9003L)
                ));

        scheduler.sendGameTomorrowReminders();

        // 호스트 1 + 승인 신청자 3 = 4회
        verify(notificationService, times(4)).createNotification(
                anyLong(),
                eq(Notification.NotificationType.GAME_TOMORROW_REMINDER),
                anyString(), anyString(), eq(500L));
        verify(notificationService, times(1)).createNotification(
                eq(7L),
                eq(Notification.NotificationType.GAME_TOMORROW_REMINDER),
                anyString(), anyString(), eq(500L));
    }

    @Test
    @DisplayName("자동 거절 환불은 신청 상태 선점 트랜잭션이 끝난 뒤 실행한다")
    void managePartyLifecycle_refundsAfterApplicationMutationTransaction() {
        Party party = newParty(700L, 7L, LocalDate.now().plusDays(2));
        PartyApplication application = newApplication(700L, 9001L);
        application.setId(800L);
        application.setIsApproved(false);
        application.setIsRejected(false);
        application.setIsPaid(true);
        application.setOrderId("MATE-700-9001");
        application.setResponseDeadline(Instant.now().minusSeconds(60));

        when(partyRepository.findByStatusAndGameDateBefore(any(), any())).thenReturn(List.of());
        when(partyRepository.findByStatusAndGameDate(any(), any())).thenReturn(List.of());
        when(partyRepository.findByStatus(Party.PartyStatus.CHECKED_IN)).thenReturn(List.of());
        when(applicationRepository.findByIsApprovedFalseAndIsRejectedFalseAndResponseDeadlineBefore(any()))
                .thenReturn(List.of(application));
        when(lifecycleMutationService.rejectExpiredApplication(
                eq(700L), eq(800L), eq(9001L), any()))
                .thenReturn(new PartyLifecycleMutationService.ExpiredApplicationMutation(application, party));
        when(lifecycleMutationService.claimRefundAttempt(eq(700L), eq(800L), eq(9001L), any()))
                .thenReturn(new PartyLifecycleMutationService.RefundAttemptClaim(application, false));
        when(paymentTransactionService.processCancellation(eq(application), any()))
                .thenReturn(canceledRefund(800L));

        scheduler.managePartyLifecycle();

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(
                lifecycleMutationService, paymentTransactionService);
        order.verify(lifecycleMutationService).rejectExpiredApplication(
                eq(700L), eq(800L), eq(9001L), any());
        order.verify(lifecycleMutationService).claimRefundAttempt(eq(700L), eq(800L), eq(9001L), any());
        order.verify(paymentTransactionService).processCancellation(eq(application), any());
        order.verify(lifecycleMutationService).markApplicationUnpaidAfterRefund(700L, 800L, 9001L);
    }

    @Test
    @DisplayName("환불 실패로 남은 결제된 자동 거절 신청은 다음 실행에서 재시도한다")
    void managePartyLifecycle_retriesPaidRejectedExpiredApplication() {
        Party party = newParty(701L, 7L, LocalDate.now().plusDays(2));
        PartyApplication application = newApplication(701L, 9002L);
        application.setId(801L);
        application.setIsApproved(false);
        application.setIsRejected(true);
        application.setIsPaid(true);
        application.setOrderId("MATE-701-9002");
        application.setResponseDeadline(Instant.now().minusSeconds(60));

        when(partyRepository.findByStatusAndGameDateBefore(any(), any())).thenReturn(List.of());
        when(partyRepository.findByStatusAndGameDate(any(), any())).thenReturn(List.of());
        when(partyRepository.findByStatus(Party.PartyStatus.CHECKED_IN)).thenReturn(List.of());
        when(applicationRepository
                .findByIsRejectedTrueAndIsPaidTrue())
                .thenReturn(List.of(application));
        when(lifecycleMutationService.claimRefundAttempt(eq(701L), eq(801L), eq(9002L), any()))
                .thenReturn(new PartyLifecycleMutationService.RefundAttemptClaim(application, false));
        when(paymentTransactionService.processCancellation(eq(application), any()))
                .thenReturn(canceledRefund(801L));

        scheduler.managePartyLifecycle();

        verify(paymentTransactionService).processCancellation(eq(application), any());
        verify(lifecycleMutationService).markApplicationUnpaidAfterRefund(701L, 801L, 9002L);
        verify(notificationService, never()).createNotification(
                anyLong(), any(), anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("자동 거절 환불 실패는 결제 실패 상태를 별도 트랜잭션으로 기록한다")
    void managePartyLifecycle_recordsRefundFailureForRetry() {
        Party party = newParty(702L, 7L, LocalDate.now().plusDays(2));
        PartyApplication application = newApplication(702L, 9003L);
        application.setId(802L);
        application.setIsApproved(false);
        application.setIsRejected(false);
        application.setIsPaid(true);
        application.setOrderId("MATE-702-9003");
        application.setResponseDeadline(Instant.now().minusSeconds(60));

        when(partyRepository.findByStatusAndGameDateBefore(any(), any())).thenReturn(List.of());
        when(partyRepository.findByStatusAndGameDate(any(), any())).thenReturn(List.of());
        when(partyRepository.findByStatus(Party.PartyStatus.CHECKED_IN)).thenReturn(List.of());
        when(applicationRepository.findByIsApprovedFalseAndIsRejectedFalseAndResponseDeadlineBefore(any()))
                .thenReturn(List.of(application));
        when(lifecycleMutationService.rejectExpiredApplication(
                eq(702L), eq(802L), eq(9003L), any()))
                .thenReturn(new PartyLifecycleMutationService.ExpiredApplicationMutation(application, party));
        when(lifecycleMutationService.claimRefundAttempt(eq(702L), eq(802L), eq(9003L), any()))
                .thenReturn(new PartyLifecycleMutationService.RefundAttemptClaim(application, false));
        when(paymentTransactionService.processCancellation(eq(application), any()))
                .thenThrow(new RuntimeException("temporary Toss failure"));

        scheduler.managePartyLifecycle();

        verify(lifecycleMutationService).recordRefundFailure("MATE-702-9003");
        verify(lifecycleMutationService, never()).markApplicationUnpaidAfterRefund(702L, 802L, 9003L);
    }

    @Test
    @DisplayName("환불 응답이 CANCELED가 아니면 결제 상태를 지우지 않는다")
    void managePartyLifecycle_keepsPaidWhenRefundResponseIsNotCanceled() {
        Party party = newParty(703L, 7L, LocalDate.now().plusDays(2));
        PartyApplication application = newApplication(703L, 9004L);
        application.setId(803L);
        application.setIsApproved(false);
        application.setIsRejected(false);
        application.setIsPaid(true);
        application.setOrderId("MATE-703-9004");
        application.setResponseDeadline(Instant.now().minusSeconds(60));

        when(partyRepository.findByStatusAndGameDateBefore(any(), any())).thenReturn(List.of());
        when(partyRepository.findByStatusAndGameDate(any(), any())).thenReturn(List.of());
        when(partyRepository.findByStatus(Party.PartyStatus.CHECKED_IN)).thenReturn(List.of());
        when(applicationRepository.findByIsApprovedFalseAndIsRejectedFalseAndResponseDeadlineBefore(any()))
                .thenReturn(List.of(application));
        when(lifecycleMutationService.rejectExpiredApplication(
                eq(703L), eq(803L), eq(9004L), any()))
                .thenReturn(new PartyLifecycleMutationService.ExpiredApplicationMutation(application, party));
        when(lifecycleMutationService.claimRefundAttempt(eq(703L), eq(803L), eq(9004L), any()))
                .thenReturn(new PartyLifecycleMutationService.RefundAttemptClaim(application, false));
        when(paymentTransactionService.processCancellation(eq(application), any()))
                .thenReturn(PartyApplicationDTO.CancelResponse.builder()
                        .applicationId(803L)
                        .refundPolicyApplied("NO_PAYMENT")
                        .build());

        scheduler.managePartyLifecycle();

        verify(lifecycleMutationService).recordRefundFailure("MATE-703-9004");
        verify(lifecycleMutationService, never()).markApplicationUnpaidAfterRefund(703L, 803L, 9004L);
    }

    @Test
    @DisplayName("결제 트랜잭션이 이미 취소됐으면 외부 취소 없이 신청만 미결제로 동기화한다")
    void managePartyLifecycle_syncsAlreadyCanceledClaimWithoutProviderCall() {
        PartyApplication application = newApplication(704L, 9005L);
        application.setId(804L);
        application.setIsRejected(true);
        application.setIsPaid(true);
        application.setOrderId("MATE-704-9005");

        when(partyRepository.findByStatusAndGameDateBefore(any(), any())).thenReturn(List.of());
        when(partyRepository.findByStatusAndGameDate(any(), any())).thenReturn(List.of());
        when(partyRepository.findByStatus(Party.PartyStatus.CHECKED_IN)).thenReturn(List.of());
        when(applicationRepository.findByIsRejectedTrueAndIsPaidTrue()).thenReturn(List.of(application));
        when(lifecycleMutationService.claimRefundAttempt(eq(704L), eq(804L), eq(9005L), any()))
                .thenReturn(new PartyLifecycleMutationService.RefundAttemptClaim(application, true));

        scheduler.managePartyLifecycle();

        verify(paymentTransactionService, never()).processCancellation(any(), any());
        verify(lifecycleMutationService).markApplicationUnpaidAfterRefund(704L, 804L, 9005L);
    }

    private PartyApplicationDTO.CancelResponse canceledRefund(Long applicationId) {
        return PartyApplicationDTO.CancelResponse.builder()
                .applicationId(applicationId)
                .paymentStatus(PaymentStatus.CANCELED)
                .build();
    }

    private Party newParty(Long id, Long hostId, LocalDate gameDate) {
        Party party = new Party();
        party.setId(id);
        party.setHostId(hostId);
        party.setGameDate(gameDate);
        party.setGameTime(LocalTime.of(18, 30));
        return party;
    }

    private PartyApplication newApplication(Long partyId, Long applicantId) {
        PartyApplication app = new PartyApplication();
        app.setPartyId(partyId);
        app.setApplicantId(applicantId);
        app.setIsApproved(true);
        return app;
    }

}
