package com.example.mate.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserProviderRepository;
import com.example.auth.repository.UserRepository;
import com.example.cheerboard.repo.CheerPostRepo;
import com.example.cheerboard.repo.CheerReportRepo;
import com.example.mate.dto.TossPaymentDTO;
import com.example.mate.entity.Party;
import com.example.mate.entity.PartyApplication;
import com.example.mate.entity.PaymentFlowType;
import com.example.mate.entity.PaymentIntent;
import com.example.mate.entity.PaymentStatus;
import com.example.mate.entity.PaymentTransaction;
import com.example.mate.entity.SettlementStatus;
import com.example.mate.repository.CheckInRecordRepository;
import com.example.mate.repository.PartyApplicationRepository;
import com.example.mate.repository.PartyRepository;
import com.example.mate.repository.PaymentIntentRepository;
import com.example.mate.repository.PaymentTransactionRepository;
import com.example.mate.service.PartyService;
import com.example.mate.service.PayoutClaimService;
import com.example.mate.service.TossPaymentService;
import com.example.mate.support.MateTestFixtureFactory;
import com.example.mate.support.MateTestTokenHelper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.doAnswer;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
        "spring.profiles.active=test",
        "spring.jwt.secret=test-jwt-secret-64-characters-long-for-hs512-signature-tests-key-1234567890",
        "spring.jwt.refresh-expiration=86400000",
        "spring.datasource.url=jdbc:h2:mem:mate_refund_reconciliation;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.jpa.open-in-view=false",
        "jobrunr.background-job-server.enabled=false",
        "jobrunr.dashboard.enabled=false",
        "mate.payment.mode=TOSS_TEST",
        "payment.payout.enabled=false",
        "storage.type=oci",
        "oci.s3.endpoint=http://localhost:4566",
        "oci.s3.access-key=test-access-key",
        "oci.s3.secret-key=test-secret-key",
        "oci.s3.bucket=test-bucket",
        "oci.s3.region=ap-seoul-1"
})
class MateRefundReconciliationIntegrationTest {

    private static final String HOST_EMAIL = "mate-refund-host@example.com";
    private static final String APPLICANT_EMAIL = "mate-refund-applicant@example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserProviderRepository userProviderRepository;

    @Autowired
    private PartyRepository partyRepository;

    @MockitoSpyBean
    private PartyApplicationRepository partyApplicationRepository;

    @Autowired
    private PaymentIntentRepository paymentIntentRepository;

    @Autowired
    private PaymentTransactionRepository paymentTransactionRepository;

    @MockitoSpyBean
    private PartyService partyService;

    @Autowired
    private PayoutClaimService payoutClaimService;

    @Autowired
    private CheckInRecordRepository checkInRecordRepository;

    @MockitoBean
    private TossPaymentService tossPaymentService;

    @MockitoBean
    private CheerPostRepo cheerPostRepo;

    @MockitoBean
    private CheerReportRepo cheerReportRepo;

    @BeforeEach
    void setUp() {
        checkInRecordRepository.deleteAll();
        paymentTransactionRepository.deleteAll();
        partyApplicationRepository.deleteAll();
        paymentIntentRepository.deleteAll();
        partyRepository.deleteAll();
        userProviderRepository.deleteAll();
        userRepository.deleteAll();

        UserEntity host = userRepository.save(MateTestFixtureFactory.user(HOST_EMAIL, "Refund Host"));
        UserEntity applicant = userRepository.save(MateTestFixtureFactory.user(APPLICANT_EMAIL, "Refund Applicant"));
        MateTestTokenHelper.register(host);
        MateTestTokenHelper.register(applicant);
        userProviderRepository.save(MateTestFixtureFactory.socialProvider(host, "kakao"));
        userProviderRepository.save(MateTestFixtureFactory.socialProvider(applicant, "naver"));
    }

    @Test
    @DisplayName("취소 API는 제공자 실제 취소 금액을 저장하고 신청자를 결제 사유 변조로부터 보호한다")
    void cancelApplication_reconcilesProviderActualAmount() throws Exception {
        Fixture fixture = savePaidApplication();
        TossPaymentDTO.CancelResponse providerResponse = new TossPaymentDTO.CancelResponse(
                fixture.paymentKey(), "DONE", fixture.grossAmount());
        providerResponse.setBalanceAmount(0);
        TossPaymentDTO.ConfirmResponse beforeCancel = new TossPaymentDTO.ConfirmResponse(
                fixture.paymentKey(), fixture.orderId(), "DONE", fixture.grossAmount(), "카드");
        beforeCancel.setBalanceAmount(fixture.grossAmount());
        given(tossPaymentService.getPayment(fixture.paymentKey())).willReturn(beforeCancel);
        given(tossPaymentService.cancelPayment(anyString(), anyString(), anyInt()))
                .willReturn(providerResponse);

        String responseJson = mockMvc.perform(post("/api/applications/{applicationId}/cancel", fixture.applicationId())
                        .with(MateTestTokenHelper.principalAs(APPLICANT_EMAIL))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "cancelReasonType", "SYSTEM",
                                "cancelMemo", "개인 사정"))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode response = objectMapper.readTree(responseJson);
        assertThat(response.get("paymentStatus").asText()).isEqualTo("CANCELED");
        assertThat(response.get("refundAmount").asInt()).isEqualTo(fixture.grossAmount());
        assertThat(partyApplicationRepository.findById(fixture.applicationId())).isEmpty();

        PaymentTransaction transaction = paymentTransactionRepository
                .findByOrderId(fixture.orderId())
                .orElseThrow();
        assertThat(transaction.getPaymentStatus()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(transaction.getRefundAmount()).isEqualTo(fixture.grossAmount());
        assertThat(transaction.getFeeAmount()).isZero();
        verify(tossPaymentService).cancelPayment(
                fixture.paymentKey(),
                "메이트 취소 처리: BUYER_CHANGED_MIND",
                19800);
    }

    @Test
    @DisplayName("제공자 취소 금액이 의도보다 작으면 신청과 환불 상태를 성공으로 확정하지 않는다")
    void cancelApplication_failsClosedWhenProviderAmountIsShort() throws Exception {
        Fixture fixture = savePaidApplication();
        TossPaymentDTO.CancelResponse providerResponse = new TossPaymentDTO.CancelResponse(
                fixture.paymentKey(), "PARTIAL_CANCELED", fixture.grossAmount());
        providerResponse.setBalanceAmount(5000);
        TossPaymentDTO.ConfirmResponse lookupResponse = new TossPaymentDTO.ConfirmResponse(
                fixture.paymentKey(), fixture.orderId(), "PARTIAL_CANCELED", fixture.grossAmount(), "카드");
        lookupResponse.setBalanceAmount(5000);
        given(tossPaymentService.cancelPayment(anyString(), anyString(), anyInt()))
                .willReturn(providerResponse);
        given(tossPaymentService.getPayment(fixture.paymentKey())).willReturn(lookupResponse);

        mockMvc.perform(post("/api/applications/{applicationId}/cancel", fixture.applicationId())
                        .with(MateTestTokenHelper.principalAs(APPLICANT_EMAIL))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "cancelReasonType", "BUYER_CHANGED_MIND",
                                "cancelMemo", "환불 확인"))))
                .andExpect(status().is5xxServerError());

        assertThat(partyApplicationRepository.findById(fixture.applicationId())).isPresent();
        PaymentTransaction failedTransaction = paymentTransactionRepository
                .findByOrderId(fixture.orderId())
                .orElseThrow();
        assertThat(failedTransaction.getPaymentStatus()).isEqualTo(PaymentStatus.REFUND_FAILED);
        assertThat(failedTransaction.getRequestedRefundAmount()).isEqualTo(19800);
        assertThat(failedTransaction.getRequestedFeeAmount()).isEqualTo(2200);
        assertThat(failedTransaction.getCancellationRequestedAt()).isNotNull();
    }

    @Test
    @DisplayName("환불 커밋 뒤 정리가 롤백돼도 D-1 이후 provider 재호출 없이 한 번만 복구한다")
    void cancelApplication_recoversCommittedRefundAfterOuterRollbackPastCutoff() throws Exception {
        Fixture fixture = savePaidApplication(true, false);
        TossPaymentDTO.CancelResponse providerResponse = new TossPaymentDTO.CancelResponse(
                fixture.paymentKey(), "DONE", fixture.grossAmount());
        providerResponse.setBalanceAmount(0);
        TossPaymentDTO.ConfirmResponse beforeCancel = new TossPaymentDTO.ConfirmResponse(
                fixture.paymentKey(), fixture.orderId(), "DONE", fixture.grossAmount(), "카드");
        beforeCancel.setBalanceAmount(fixture.grossAmount());
        given(tossPaymentService.getPayment(fixture.paymentKey())).willReturn(beforeCancel);
        given(tossPaymentService.cancelPayment(anyString(), anyString(), anyInt()))
                .willReturn(providerResponse);

        AtomicBoolean failFirstCleanup = new AtomicBoolean(true);
        doAnswer(invocation -> {
            if (failFirstCleanup.compareAndSet(true, false)) {
                throw new IllegalStateException("synthetic outer cleanup failure");
            }
            return invocation.callRealMethod();
        }).when(partyService).decrementParticipants(fixture.partyId());

        String cancelBody = objectMapper.writeValueAsString(Map.of(
                "cancelReasonType", "BUYER_CHANGED_MIND",
                "cancelMemo", "synthetic rollback/recovery"));

        mockMvc.perform(post("/api/applications/{applicationId}/cancel", fixture.applicationId())
                        .with(MateTestTokenHelper.principalAs(APPLICANT_EMAIL))
                        .contentType("application/json")
                        .content(cancelBody))
                .andExpect(status().is5xxServerError());

        assertThat(partyApplicationRepository.findById(fixture.applicationId())).isPresent();
        assertThat(partyRepository.findById(fixture.partyId()).orElseThrow().getCurrentParticipants()).isEqualTo(2);
        assertThat(paymentIntentRepository.findByOrderId(fixture.orderId()).orElseThrow().getStatus())
                .isEqualTo(PaymentIntent.IntentStatus.APPLICATION_CREATED);
        PaymentTransaction committedCancellation = paymentTransactionRepository
                .findByOrderId(fixture.orderId()).orElseThrow();
        assertThat(committedCancellation.getPaymentStatus()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(committedCancellation.getProviderReconciledAt()).isNotNull();

        Party afterCutoff = partyRepository.findById(fixture.partyId()).orElseThrow();
        afterCutoff.setGameDate(LocalDate.now().minusDays(1));
        partyRepository.saveAndFlush(afterCutoff);

        mockMvc.perform(post("/api/applications/{applicationId}/cancel", fixture.applicationId())
                        .with(MateTestTokenHelper.principalAs(APPLICANT_EMAIL))
                        .contentType("application/json")
                        .content(cancelBody))
                .andExpect(status().isOk());

        assertThat(partyApplicationRepository.findById(fixture.applicationId())).isEmpty();
        assertThat(partyRepository.findById(fixture.partyId()).orElseThrow().getCurrentParticipants()).isEqualTo(1);
        assertThat(paymentIntentRepository.findByOrderId(fixture.orderId()).orElseThrow().getStatus())
                .isEqualTo(PaymentIntent.IntentStatus.CANCELED);
        verify(tossPaymentService).cancelPayment(
                fixture.paymentKey(), "메이트 취소 처리: BUYER_CHANGED_MIND", 19800);
    }

    @Test
    @DisplayName("취소가 읽어둔 신청 뒤 승인이 커밋되면 최신 상태 기준으로 인원을 복구한다")
    void cancelRefreshesApplicationStateReadBeforeConcurrentApprovalCommit() throws Exception {
        Fixture fixture = savePaidApplication(false, false);
        TossPaymentDTO.CancelResponse providerResponse = new TossPaymentDTO.CancelResponse(
                fixture.paymentKey(), "DONE", fixture.grossAmount());
        providerResponse.setBalanceAmount(0);
        TossPaymentDTO.ConfirmResponse beforeCancel = new TossPaymentDTO.ConfirmResponse(
                fixture.paymentKey(), fixture.orderId(), "DONE", fixture.grossAmount(), "카드");
        beforeCancel.setBalanceAmount(fixture.grossAmount());
        given(tossPaymentService.getPayment(fixture.paymentKey())).willReturn(beforeCancel);
        given(tossPaymentService.cancelPayment(anyString(), anyString(), anyInt()))
                .willReturn(providerResponse);

        CountDownLatch cancelCandidateLoaded = new CountDownLatch(1);
        CountDownLatch approvalCommitted = new CountDownLatch(1);
        ExecutorService cancelExecutor = Executors.newSingleThreadExecutor(
                task -> new Thread(task, "mate-cancel-request"));
        ExecutorService approveExecutor = Executors.newSingleThreadExecutor(
                task -> new Thread(task, "mate-approve-request"));
        String cancelBody = objectMapper.writeValueAsString(Map.of(
                "cancelReasonType", "BUYER_CHANGED_MIND",
                "cancelMemo", "synthetic approval/cancel race"));
        Long applicantId = userRepository.findByEmail(APPLICANT_EMAIL).orElseThrow().getId();
        PartyApplication candidate = partyApplicationRepository.findById(fixture.applicationId()).orElseThrow();
        doAnswer(invocation -> {
            if (Thread.currentThread().getName().equals("mate-cancel-request")) {
                cancelCandidateLoaded.countDown();
                if (!approvalCommitted.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("approval did not commit after the cancellation candidate read");
                }
            }
            return java.util.Optional.of(candidate);
        }).when(partyApplicationRepository).findByIdAndApplicantId(
                fixture.applicationId(), applicantId);
        try {
            Future<String> cancel = cancelExecutor.submit(() -> {
                var result = mockMvc.perform(post("/api/applications/{applicationId}/cancel", fixture.applicationId())
                                .with(MateTestTokenHelper.principalAs(APPLICANT_EMAIL))
                                .contentType("application/json")
                                .content(cancelBody))
                        .andReturn();
                return result.getResponse().getStatus() + "|" + result.getResponse().getContentAsString();
            });
            boolean candidateLoaded = cancelCandidateLoaded.await(5, TimeUnit.SECONDS);
            if (!candidateLoaded) {
                throw new AssertionError("cancel response before candidate load: " + cancel.get(1, TimeUnit.SECONDS));
            }
            Future<Integer> approve = approveExecutor.submit(() -> {
                try {
                    return mockMvc.perform(post("/api/applications/{applicationId}/approve", fixture.applicationId())
                                    .with(MateTestTokenHelper.principalAs(HOST_EMAIL)))
                            .andReturn().getResponse().getStatus();
                } finally {
                    approvalCommitted.countDown();
                }
            });

            String cancelResult = cancel.get(20, TimeUnit.SECONDS);
            assertThat(cancelResult).as(cancelResult).startsWith("200|");
            assertThat(approve.get(20, TimeUnit.SECONDS)).isEqualTo(200);
        } finally {
            approvalCommitted.countDown();
            cancelExecutor.shutdownNow();
            approveExecutor.shutdownNow();
            cancelExecutor.awaitTermination(5, TimeUnit.SECONDS);
            approveExecutor.awaitTermination(5, TimeUnit.SECONDS);
        }

        assertThat(partyApplicationRepository.findById(fixture.applicationId())).isEmpty();
        assertThat(partyRepository.findById(fixture.partyId()).orElseThrow().getCurrentParticipants()).isEqualTo(1);
        assertThat(paymentIntentRepository.findByOrderId(fixture.orderId()).orElseThrow().getStatus())
                .isEqualTo(PaymentIntent.IntentStatus.CANCELED);
        assertThat(paymentTransactionRepository.findByOrderId(fixture.orderId()).orElseThrow().getPaymentStatus())
                .isEqualTo(PaymentStatus.CANCELED);
        verify(tossPaymentService).cancelPayment(
                fixture.paymentKey(), "메이트 취소 처리: BUYER_CHANGED_MIND", 19800);
    }

    @Test
    @DisplayName("같은 신청의 동시 취소는 환불과 참여 인원을 한 번만 반영한다")
    void concurrentApplicantCancellationsRefundAndDecrementParticipantsOnlyOnce() throws Exception {
        Fixture fixture = savePaidApplication(true, false);
        TossPaymentDTO.CancelResponse providerResponse = new TossPaymentDTO.CancelResponse(
                fixture.paymentKey(), "DONE", fixture.grossAmount());
        providerResponse.setBalanceAmount(0);
        TossPaymentDTO.ConfirmResponse beforeCancel = new TossPaymentDTO.ConfirmResponse(
                fixture.paymentKey(), fixture.orderId(), "DONE", fixture.grossAmount(), "카드");
        beforeCancel.setBalanceAmount(fixture.grossAmount());
        CountDownLatch providerLookupStarted = new CountDownLatch(1);
        CountDownLatch allowProviderLookupToFinish = new CountDownLatch(1);
        CountDownLatch secondCancelStarted = new CountDownLatch(1);
        given(tossPaymentService.getPayment(fixture.paymentKey())).willAnswer(invocation -> {
            providerLookupStarted.countDown();
            if (!allowProviderLookupToFinish.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("timed out waiting for synthetic provider release");
            }
            return beforeCancel;
        });
        given(tossPaymentService.cancelPayment(anyString(), anyString(), anyInt()))
                .willReturn(providerResponse);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        String cancelBody = objectMapper.writeValueAsString(Map.of(
                "cancelReasonType", "BUYER_CHANGED_MIND",
                "cancelMemo", "synthetic duplicate-cancel race"));
        try {
            Future<Integer> firstCancel = executor.submit(() -> mockMvc.perform(
                            post("/api/applications/{applicationId}/cancel", fixture.applicationId())
                                    .with(MateTestTokenHelper.principalAs(APPLICANT_EMAIL))
                                    .contentType("application/json")
                                    .content(cancelBody))
                    .andReturn().getResponse().getStatus());
            assertThat(providerLookupStarted.await(10, TimeUnit.SECONDS)).isTrue();

            Future<Integer> secondCancel = executor.submit(() -> {
                secondCancelStarted.countDown();
                return mockMvc.perform(post("/api/applications/{applicationId}/cancel", fixture.applicationId())
                                .with(MateTestTokenHelper.principalAs(APPLICANT_EMAIL))
                                .contentType("application/json")
                                .content(cancelBody))
                        .andReturn().getResponse().getStatus();
            });
            assertThat(secondCancelStarted.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(secondCancel.isDone()).isFalse();

            allowProviderLookupToFinish.countDown();
            assertThat(firstCancel.get(20, TimeUnit.SECONDS)).isEqualTo(200);
            assertThat(secondCancel.get(20, TimeUnit.SECONDS)).isEqualTo(404);
        } finally {
            allowProviderLookupToFinish.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }

        assertThat(partyApplicationRepository.findById(fixture.applicationId())).isEmpty();
        assertThat(partyRepository.findById(fixture.partyId()).orElseThrow().getCurrentParticipants()).isEqualTo(1);
        assertThat(paymentTransactionRepository.findByOrderId(fixture.orderId()).orElseThrow().getPaymentStatus())
                .isEqualTo(PaymentStatus.CANCELED);
        verify(tossPaymentService).cancelPayment(
                fixture.paymentKey(), "메이트 취소 처리: BUYER_CHANGED_MIND", 19800);
        verify(partyService).decrementParticipants(fixture.partyId());
    }

    @Test
    @DisplayName("취소가 provider 확인 중인 결제는 동시 payout claim이 지급 provider 호출 대상으로 잡지 않는다")
    void cancellationAndPayoutClaimDoNotClaimRefundingPayment() throws Exception {
        Fixture fixture = savePaidApplication(true, false);
        TossPaymentDTO.CancelResponse providerResponse = new TossPaymentDTO.CancelResponse(
                fixture.paymentKey(), "DONE", fixture.grossAmount());
        providerResponse.setBalanceAmount(0);
        TossPaymentDTO.ConfirmResponse beforeCancel = new TossPaymentDTO.ConfirmResponse(
                fixture.paymentKey(), fixture.orderId(), "DONE", fixture.grossAmount(), "카드");
        beforeCancel.setBalanceAmount(fixture.grossAmount());
        CountDownLatch providerLookupStarted = new CountDownLatch(1);
        CountDownLatch allowProviderLookupToFinish = new CountDownLatch(1);
        given(tossPaymentService.getPayment(fixture.paymentKey())).willAnswer(invocation -> {
            providerLookupStarted.countDown();
            if (!allowProviderLookupToFinish.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("timed out waiting for synthetic provider release");
            }
            return beforeCancel;
        });
        given(tossPaymentService.cancelPayment(anyString(), anyString(), anyInt()))
                .willReturn(providerResponse);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        String cancelBody = objectMapper.writeValueAsString(Map.of(
                "cancelReasonType", "BUYER_CHANGED_MIND",
                "cancelMemo", "synthetic payout-claim race"));
        try {
            Future<Integer> cancel = executor.submit(() -> mockMvc.perform(
                            post("/api/applications/{applicationId}/cancel", fixture.applicationId())
                                    .with(MateTestTokenHelper.principalAs(APPLICANT_EMAIL))
                                    .contentType("application/json")
                                    .content(cancelBody))
                    .andReturn().getResponse().getStatus());
            assertThat(providerLookupStarted.await(10, TimeUnit.SECONDS)).isTrue();

            PaymentTransaction prepared = paymentTransactionRepository.findByOrderId(fixture.orderId()).orElseThrow();
            assertThat(prepared.getPaymentStatus()).isEqualTo(PaymentStatus.REFUND_REQUESTED);
            CountDownLatch payoutClaimStarted = new CountDownLatch(1);
            Future<PayoutClaimService.ClaimedPayout> claim = executor.submit(() -> {
                payoutClaimStarted.countDown();
                return payoutClaimService.claimInitial(prepared.getId(), true);
            });
            assertThat(payoutClaimStarted.await(10, TimeUnit.SECONDS)).isTrue();

            allowProviderLookupToFinish.countDown();
            assertThat(cancel.get(20, TimeUnit.SECONDS)).isEqualTo(200);
            assertThat(claim.get(20, TimeUnit.SECONDS).action()).isEqualTo(PayoutClaimService.ClaimAction.NONE);
        } finally {
            allowProviderLookupToFinish.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }

        assertThat(partyApplicationRepository.findById(fixture.applicationId())).isEmpty();
        assertThat(partyRepository.findById(fixture.partyId()).orElseThrow().getCurrentParticipants()).isEqualTo(1);
        assertThat(paymentTransactionRepository.findByOrderId(fixture.orderId()).orElseThrow().getPaymentStatus())
                .isEqualTo(PaymentStatus.CANCELED);
        verify(tossPaymentService).cancelPayment(
                fixture.paymentKey(), "메이트 취소 처리: BUYER_CHANGED_MIND", 19800);
    }

    private Fixture savePaidApplication() {
        return savePaidApplication(false, false);
    }

    private Fixture savePaidApplication(boolean approved, boolean pastCutoff) {
        UserEntity host = userRepository.findByEmail(HOST_EMAIL).orElseThrow();
        UserEntity applicant = userRepository.findByEmail(APPLICANT_EMAIL).orElseThrow();
        Party party = partyRepository.save(MateTestFixtureFactory.party(
                host.getId(),
                host.getName(),
                Party.PartyStatus.PENDING,
                4,
                approved ? 2 : 1,
                12000,
                null,
                pastCutoff ? LocalDate.now().minusDays(1) : LocalDate.now().plusDays(1),
                LocalTime.of(18, 30)));
        String orderId = "MATE-REFUND-" + party.getId();
        String paymentKey = "refund-pay-key-" + party.getId();
        PartyApplication application = partyApplicationRepository.save(
                MateTestFixtureFactory.application(
                        party.getId(),
                        applicant.getId(),
                        applicant.getName(),
                        PartyApplication.PaymentType.DEPOSIT,
                        true,
                        approved,
                        false,
                        orderId,
                        paymentKey));
        int grossAmount = application.getDepositAmount();
        paymentTransactionRepository.save(PaymentTransaction.builder()
                .partyId(party.getId())
                .applicationId(application.getId())
                .buyerUserId(applicant.getId())
                .sellerUserId(host.getId())
                .flowType(PaymentFlowType.DEPOSIT)
                .orderId(orderId)
                .paymentKey(paymentKey)
                .grossAmount(grossAmount)
                .feeAmount(0)
                .refundAmount(0)
                .netAmount(grossAmount)
                .paymentStatus(PaymentStatus.PAID)
                .settlementStatus(SettlementStatus.PENDING)
                .build());
        paymentIntentRepository.save(PaymentIntent.builder()
                .orderId(orderId)
                .partyId(party.getId())
                .applicantId(applicant.getId())
                .expectedAmount(grossAmount)
                .currency("KRW")
                .flowType(PaymentFlowType.DEPOSIT)
                .paymentType(PartyApplication.PaymentType.DEPOSIT)
                .mode(PaymentIntent.IntentMode.PREPARED)
                .status(PaymentIntent.IntentStatus.APPLICATION_CREATED)
                .paymentKey(paymentKey)
                .build());
        return new Fixture(party.getId(), application.getId(), orderId, paymentKey, grossAmount);
    }

    private record Fixture(Long partyId, Long applicationId, String orderId, String paymentKey, int grossAmount) {
    }
}
