package com.example.mate.service;

import com.example.auth.service.UserService;
import com.example.auth.entity.UserEntity;
import com.example.kbo.service.TicketVerificationTokenStore;
import com.example.mate.dto.PartyApplicationDTO;
import com.example.mate.entity.CancelReasonType;
import com.example.mate.entity.Party;
import com.example.mate.entity.PartyApplication;
import com.example.mate.entity.PaymentStatus;
import com.example.mate.exception.InvalidApplicationStatusException;
import com.example.mate.exception.PartyApplicationNotFoundException;
import com.example.mate.repository.PartyApplicationRepository;
import com.example.mate.repository.PartyRepository;
import com.example.mate.repository.PartyReviewRepository;
import com.example.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PartyApplicationServicePaymentPolicyTest {

    @Mock
    private PartyApplicationRepository applicationRepository;
    @Mock
    private PartyRepository partyRepository;
    @Mock
    private PartyReviewRepository partyReviewRepository;
    @Mock
    private PartyService partyService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private UserService userService;
    @Mock
    private TicketVerificationTokenStore ticketVerificationTokenStore;
    @Mock
    private PaymentTransactionService paymentTransactionService;
    @Mock
    private MatePaymentModeService matePaymentModeService;

    @InjectMocks
    private PartyApplicationService partyApplicationService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(partyApplicationService, "sellingPaymentEnforced", true);
    }

    @Test
    void createApplication_fullPaymentWithoutTossFlow_throws() {
        Long applicantId = 7L;
        Party party = Party.builder()
                .id(1L)
                .hostId(99L)
                .status(Party.PartyStatus.PENDING)
                .currentParticipants(0)
                .maxParticipants(3)
                .gameDate(LocalDate.now().plusDays(2))
                .homeTeam("LG")
                .awayTeam("OB")
                .build();
        UserEntity applicant = UserEntity.builder()
                .id(applicantId)
                .name("테스터")
                .build();

        given(matePaymentModeService.isTossTest()).willReturn(true);
        given(matePaymentModeService.isInAppPayment()).willReturn(true);
        given(userService.getUserIdByEmail("user@example.com")).willReturn(applicantId);
        given(userService.findUserByIdForUpdate(applicantId)).willReturn(applicant);
        given(userService.isSocialVerified(applicantId)).willReturn(true);
        given(applicationRepository.findByPartyIdAndApplicantId(1L, applicantId)).willReturn(Optional.empty());
        given(applicationRepository.existsByPartyIdAndApplicantIdAndIsRejectedTrue(1L, applicantId)).willReturn(false);
        given(applicationRepository.countByPartyIdAndIsApprovedFalseAndIsRejectedFalse(1L)).willReturn(0L);
        given(applicationRepository.countByApplicantIdAndIsApprovedTrue(applicantId)).willReturn(0L);
        given(partyRepository.findByIdForUpdate(1L)).willReturn(Optional.of(party));

        PartyApplicationDTO.Request request = PartyApplicationDTO.Request.builder()
                .partyId(1L)
                .message("함께 관람 신청을 진행합니다.")
                .depositAmount(50000)
                .paymentType(PartyApplication.PaymentType.FULL)
                .build();

        assertThatThrownBy(() -> partyApplicationService.createApplication(request, () -> "user@example.com"))
                .isInstanceOf(InvalidApplicationStatusException.class)
                .hasMessageContaining("결제 승인 API");
    }

    @Test
    void createApplication_pendingDeletionApplicant_isRejectedAfterPartyAndUserLocks() {
        Long applicantId = 17L;
        Party party = Party.builder()
                .id(2L)
                .hostId(99L)
                .status(Party.PartyStatus.PENDING)
                .currentParticipants(0)
                .maxParticipants(3)
                .build();
        UserEntity applicant = UserEntity.builder()
                .id(applicantId)
                .name("탈퇴예정")
                .enabled(false)
                .pendingDeletion(true)
                .build();
        PartyApplicationDTO.Request request = PartyApplicationDTO.Request.builder()
                .partyId(2L)
                .message("함께 즐겁게 관람하고 싶어 신청합니다.")
                .paymentType(PartyApplication.PaymentType.DEPOSIT)
                .build();

        given(partyRepository.findByIdForUpdate(2L)).willReturn(Optional.of(party));
        given(userService.findUserByIdForUpdate(applicantId)).willReturn(applicant);

        assertThatThrownBy(() -> partyApplicationService.createApplication(request, applicantId))
                .isInstanceOf(InvalidApplicationStatusException.class)
                .hasMessageContaining("탈퇴");

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(partyRepository, userService);
        order.verify(partyRepository).findByIdForUpdate(2L);
        order.verify(userService).findUserByIdForUpdate(applicantId);
        verify(applicationRepository, never()).saveAndFlush(any());
    }

    @Test
    void cancelApplication_approvedAndGameTomorrow_throwsBeforeRefund() {
        Long applicationId = 11L;
        Long applicantId = 7L;
        PartyApplication application = PartyApplication.builder()
                .id(applicationId)
                .partyId(5L)
                .applicantId(applicantId)
                .isApproved(true)
                .isRejected(false)
                .isPaid(true)
                .build();
        Party party = Party.builder()
                .id(5L)
                .gameDate(LocalDate.now())
                .status(Party.PartyStatus.MATCHED)
                .build();

        given(userService.getUserIdByEmail("user@example.com")).willReturn(applicantId);
        given(applicationRepository.findByIdAndApplicantId(applicationId, applicantId)).willReturn(Optional.of(application));
        given(partyRepository.findByIdForUpdate(5L)).willReturn(Optional.of(party));
        given(applicationRepository.findByIdAndApplicantIdForUpdate(applicationId, applicantId)).willReturn(Optional.of(application));
        given(partyRepository.findById(5L)).willReturn(Optional.of(party));

        assertThatThrownBy(() -> partyApplicationService.cancelApplication(
                applicationId,
                () -> "user@example.com",
                PartyApplicationDTO.CancelRequest.builder()
                        .cancelReasonType(CancelReasonType.BUYER_CHANGED_MIND)
                        .build()))
                .isInstanceOf(InvalidApplicationStatusException.class)
                .hasMessageContaining("경기 하루 전");

        verify(paymentTransactionService, never()).processCancellation(any(), any());
        verify(applicationRepository, never()).delete(any());
    }

    @Test
    void cancelApplication_publicApplicantCannotOverrideBuyerChangedMindReason() {
        Long applicationId = 12L;
        Long applicantId = 7L;
        PartyApplication application = PartyApplication.builder()
                .id(applicationId)
                .partyId(6L)
                .applicantId(applicantId)
                .isApproved(false)
                .isRejected(false)
                .isPaid(true)
                .build();
        Party party = Party.builder()
                .id(6L)
                .gameDate(LocalDate.now().plusDays(3))
                .status(Party.PartyStatus.PENDING)
                .build();
        PartyApplicationDTO.CancelResponse cancelResponse = PartyApplicationDTO.CancelResponse.builder()
                .applicationId(applicationId)
                .paymentStatus(PaymentStatus.CANCELED)
                .build();

        given(applicationRepository.findByIdAndApplicantId(applicationId, applicantId))
                .willReturn(Optional.of(application));
        given(partyRepository.findByIdForUpdate(6L)).willReturn(Optional.of(party));
        given(applicationRepository.findByIdAndApplicantIdForUpdate(applicationId, applicantId))
                .willReturn(Optional.of(application));
        given(partyRepository.findById(6L)).willReturn(Optional.of(party));
        given(paymentTransactionService.processCancellation(any(), any())).willReturn(cancelResponse);

        partyApplicationService.cancelApplication(
                applicationId,
                applicantId,
                PartyApplicationDTO.CancelRequest.builder()
                        .cancelReasonType(CancelReasonType.SYSTEM)
                        .cancelMemo("개인 일정이 생겼습니다.")
                        .build());

        ArgumentCaptor<PartyApplicationDTO.CancelRequest> requestCaptor =
                ArgumentCaptor.forClass(PartyApplicationDTO.CancelRequest.class);
        verify(paymentTransactionService).processCancellation(any(), requestCaptor.capture());
        assertThat(requestCaptor.getValue().getCancelReasonType())
                .isEqualTo(CancelReasonType.BUYER_CHANGED_MIND);
        assertThat(requestCaptor.getValue().getCancelMemo()).isEqualTo("개인 일정이 생겼습니다.");
    }

    @Test
    void approveApplication_nonHostApplicationId_isTreatedAsNotFoundBeforeMutation() {
        given(userService.getUserIdByEmail("host@example.com")).willReturn(99L);
        given(applicationRepository.findByIdAndPartyHostId(11L, 99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> partyApplicationService.approveApplication(11L, () -> "host@example.com"))
                .isInstanceOf(PartyApplicationNotFoundException.class);

        verify(applicationRepository, never()).save(any());
        verify(partyService, never()).incrementParticipants(any());
    }

    @Test
    void approveApplication_failedParty_isRejectedAfterPartyLock() {
        PartyApplication application = PartyApplication.builder()
                .id(11L)
                .partyId(5L)
                .applicantId(7L)
                .isApproved(false)
                .isRejected(false)
                .build();
        Party party = Party.builder().id(5L).hostId(99L).status(Party.PartyStatus.FAILED).build();

        given(applicationRepository.findByIdAndPartyHostId(11L, 99L)).willReturn(Optional.of(application));
        given(partyRepository.findByIdForUpdate(5L)).willReturn(Optional.of(party));
        given(applicationRepository.findByIdAndPartyHostIdForUpdate(11L, 99L))
                .willReturn(Optional.of(application));
        given(partyRepository.findById(5L)).willReturn(Optional.of(party));

        assertThatThrownBy(() -> partyApplicationService.approveApplication(11L, 99L))
                .isInstanceOf(InvalidApplicationStatusException.class)
                .hasMessageContaining("모집 중");

        verify(applicationRepository, never()).save(any());
        verify(partyService, never()).incrementParticipants(any());
    }

    @Test
    void approveApplication_pendingDeletionApplicant_isRejectedAfterLocks() {
        PartyApplication application = PartyApplication.builder()
                .id(11L)
                .partyId(5L)
                .applicantId(7L)
                .isApproved(false)
                .isRejected(false)
                .build();
        Party party = Party.builder().id(5L).hostId(99L).status(Party.PartyStatus.PENDING).build();
        UserEntity applicant = UserEntity.builder().id(7L).enabled(false).pendingDeletion(true).build();

        given(applicationRepository.findByIdAndPartyHostId(11L, 99L)).willReturn(Optional.of(application));
        given(partyRepository.findByIdForUpdate(5L)).willReturn(Optional.of(party));
        given(applicationRepository.findByIdAndPartyHostIdForUpdate(11L, 99L))
                .willReturn(Optional.of(application));
        given(partyRepository.findById(5L)).willReturn(Optional.of(party));
        given(userService.findUserByIdForUpdate(7L)).willReturn(applicant);

        assertThatThrownBy(() -> partyApplicationService.approveApplication(11L, 99L))
                .isInstanceOf(InvalidApplicationStatusException.class)
                .hasMessageContaining("탈퇴");

        verify(applicationRepository, never()).save(any());
        verify(partyService, never()).incrementParticipants(any());
        verify(userService).findUserByIdForUpdate(7L);
    }

    @Test
    void rejectApplication_nonHostApplicationId_isTreatedAsNotFoundBeforeMutation() {
        given(userService.getUserIdByEmail("host@example.com")).willReturn(99L);
        given(applicationRepository.findByIdAndPartyHostId(11L, 99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> partyApplicationService.rejectApplication(11L, () -> "host@example.com"))
                .isInstanceOf(PartyApplicationNotFoundException.class);

        verify(applicationRepository, never()).save(any());
        verify(paymentTransactionService, never()).processCancellation(any(), any());
    }

    @Test
    void rejectApplication_paidRefundSuccess_marksApplicationUnpaid() {
        PartyApplication application = PartyApplication.builder()
                .id(12L)
                .partyId(6L)
                .applicantId(8L)
                .isApproved(false)
                .isRejected(false)
                .isPaid(true)
                .build();
        Party party = Party.builder().id(6L).hostId(99L).status(Party.PartyStatus.PENDING).build();

        given(applicationRepository.findByIdAndPartyHostId(12L, 99L)).willReturn(Optional.of(application));
        given(partyRepository.findByIdForUpdate(6L)).willReturn(Optional.of(party));
        given(applicationRepository.findByIdAndPartyHostIdForUpdate(12L, 99L))
                .willReturn(Optional.of(application));
        given(applicationRepository.save(application)).willReturn(application);
        given(paymentTransactionService.processCancellation(any(), any()))
                .willReturn(PartyApplicationDTO.CancelResponse.builder()
                        .applicationId(12L)
                        .paymentStatus(PaymentStatus.CANCELED)
                        .build());
        given(userService.findUserById(8L))
                .willReturn(UserEntity.builder().id(8L).handle("applicant-8").build());

        partyApplicationService.rejectApplication(12L, 99L);

        assertThat(application.getIsRejected()).isTrue();
        assertThat(application.getIsPaid()).isFalse();
    }

    @Test
    void rejectApplication_paidRefundWithoutCanceledStatus_doesNotPersistRejection() {
        PartyApplication application = PartyApplication.builder()
                .id(14L)
                .partyId(8L)
                .applicantId(10L)
                .isApproved(false)
                .isRejected(false)
                .isPaid(true)
                .build();
        Party party = Party.builder().id(8L).hostId(99L).status(Party.PartyStatus.PENDING).build();

        given(applicationRepository.findByIdAndPartyHostId(14L, 99L)).willReturn(Optional.of(application));
        given(partyRepository.findByIdForUpdate(8L)).willReturn(Optional.of(party));
        given(applicationRepository.findByIdAndPartyHostIdForUpdate(14L, 99L))
                .willReturn(Optional.of(application));
        given(paymentTransactionService.processCancellation(any(), any()))
                .willReturn(PartyApplicationDTO.CancelResponse.builder()
                        .applicationId(14L)
                        .refundPolicyApplied("NO_PAYMENT")
                        .build());

        assertThatThrownBy(() -> partyApplicationService.rejectApplication(14L, 99L))
                .isInstanceOf(InvalidApplicationStatusException.class)
                .hasMessageContaining("환불 완료");

        assertThat(application.getIsRejected()).isFalse();
        assertThat(application.getIsPaid()).isTrue();
        verify(applicationRepository, never()).save(any());
    }

    @Test
    void rejectApplication_paidRefundFailure_doesNotPersistRejection() {
        PartyApplication application = PartyApplication.builder()
                .id(13L)
                .partyId(7L)
                .applicantId(9L)
                .isApproved(false)
                .isRejected(false)
                .isPaid(true)
                .build();
        Party party = Party.builder().id(7L).hostId(99L).status(Party.PartyStatus.PENDING).build();

        given(applicationRepository.findByIdAndPartyHostId(13L, 99L)).willReturn(Optional.of(application));
        given(partyRepository.findByIdForUpdate(7L)).willReturn(Optional.of(party));
        given(applicationRepository.findByIdAndPartyHostIdForUpdate(13L, 99L))
                .willReturn(Optional.of(application));
        given(paymentTransactionService.processCancellation(any(), any()))
                .willThrow(new RuntimeException("temporary Toss failure"));

        assertThatThrownBy(() -> partyApplicationService.rejectApplication(13L, 99L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("temporary Toss failure");

        assertThat(application.getIsRejected()).isFalse();
        assertThat(application.getIsPaid()).isTrue();
        verify(applicationRepository, never()).save(any());
    }

    @Test
    void cancelApplication_nonApplicantApplicationId_isTreatedAsNotFoundBeforeRefund() {
        given(userService.getUserIdByEmail("user@example.com")).willReturn(7L);
        given(applicationRepository.findByIdAndApplicantId(11L, 7L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> partyApplicationService.cancelApplication(
                11L,
                () -> "user@example.com",
                PartyApplicationDTO.CancelRequest.builder()
                        .cancelReasonType(CancelReasonType.BUYER_CHANGED_MIND)
                        .build()))
                .isInstanceOf(PartyApplicationNotFoundException.class);

        verify(paymentTransactionService, never()).processCancellation(any(), any());
        verify(applicationRepository, never()).delete(any());
    }
}
