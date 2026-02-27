package com.example.mate.service;

import com.example.auth.service.UserService;
import com.example.auth.dto.UserDto;
import com.example.kbo.service.TicketVerificationTokenStore;
import com.example.mate.dto.PartyApplicationDTO;
import com.example.mate.entity.CancelReasonType;
import com.example.mate.entity.Party;
import com.example.mate.entity.PartyApplication;
import com.example.mate.exception.InvalidApplicationStatusException;
import com.example.mate.repository.PartyApplicationRepository;
import com.example.mate.repository.PartyRepository;
import com.example.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;

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
        UserDto applicant = UserDto.builder()
                .name("테스터")
                .build();

        given(matePaymentModeService.isTossTest()).willReturn(true);
        given(userService.getUserIdByEmail("user@example.com")).willReturn(applicantId);
        given(userService.findUserByEmail("user@example.com")).willReturn(applicant);
        given(userService.isSocialVerified(applicantId)).willReturn(true);
        given(applicationRepository.findByPartyIdAndApplicantId(1L, applicantId)).willReturn(Optional.empty());
        given(applicationRepository.existsByPartyIdAndApplicantIdAndIsRejectedTrue(1L, applicantId)).willReturn(false);
        given(applicationRepository.countByPartyIdAndIsApprovedFalseAndIsRejectedFalse(1L)).willReturn(0L);
        given(applicationRepository.countByApplicantIdAndIsApprovedTrue(applicantId)).willReturn(0L);
        given(partyRepository.findById(1L)).willReturn(Optional.of(party));

        PartyApplicationDTO.Request request = PartyApplicationDTO.Request.builder()
                .partyId(1L)
                .message("신청합니다")
                .depositAmount(50000)
                .paymentType(PartyApplication.PaymentType.FULL)
                .build();

        assertThatThrownBy(() -> partyApplicationService.createApplication(request, () -> "user@example.com"))
                .isInstanceOf(InvalidApplicationStatusException.class)
                .hasMessageContaining("결제 승인 API");
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
        given(applicationRepository.findById(applicationId)).willReturn(Optional.of(application));
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
}
