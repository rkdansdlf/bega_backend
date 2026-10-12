package com.example.mate.integration;

import com.example.cheerboard.repo.CheerPostRepo;
import com.example.cheerboard.repo.CheerReportRepo;
import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserRepository;
import com.example.mate.entity.Party;
import com.example.mate.entity.PartyApplication;
import com.example.mate.entity.PaymentFlowType;
import com.example.mate.entity.PaymentStatus;
import com.example.mate.entity.PaymentTransaction;
import com.example.mate.entity.PayoutTransaction;
import com.example.mate.entity.SellerPayoutProfile;
import com.example.mate.entity.SettlementStatus;
import com.example.mate.repository.PartyRepository;
import com.example.mate.repository.PaymentTransactionRepository;
import com.example.mate.repository.PartyApplicationRepository;
import com.example.mate.repository.PayoutTransactionRepository;
import com.example.mate.repository.SellerPayoutProfileRepository;
import com.example.mate.service.PaymentTransactionService;
import com.example.mate.service.PartyLifecycleMutationService;
import com.example.mate.service.PayoutService;
import com.example.mate.service.payout.PayoutGateway;
import org.jobrunr.jobs.lambdas.JobLambda;
import org.jobrunr.scheduling.JobScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatusCode;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.profiles.active=test",
        "spring.jwt.secret=test-jwt-secret-64-characters-long-for-hs512-signature-tests-key-1234567890",
        "spring.jwt.refresh-expiration=86400000",
        "spring.datasource.url=jdbc:h2:mem:mate_payout_toss_integration;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.jpa.open-in-view=false",
        "jobrunr.background-job-server.enabled=false",
        "jobrunr.dashboard.enabled=false",
        "mate.payment.mode=TOSS_TEST",
        "payment.payout.enabled=true",
        "payment.payout.provider=SIM",
        "storage.type=oci",
        "oci.s3.endpoint=http://localhost:4566",
        "oci.s3.access-key=test-access-key",
        "oci.s3.secret-key=test-secret-key",
        "oci.s3.bucket=test-bucket",
        "oci.s3.region=ap-seoul-1"
})
class PayoutTossProviderIntegrationTest {

    @Autowired
    private PaymentTransactionService paymentTransactionService;

    @Autowired
    private PayoutService payoutService;

    @Autowired
    private PaymentTransactionRepository paymentTransactionRepository;

    @Autowired
    private PartyApplicationRepository partyApplicationRepository;

    @Autowired
    private PartyRepository partyRepository;

    @Autowired
    private PayoutTransactionRepository payoutTransactionRepository;

    @Autowired
    private SellerPayoutProfileRepository sellerPayoutProfileRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ControlledTossGateway controlledTossGateway;

    @MockitoBean
    private JobScheduler jobScheduler;

    @MockitoBean
    private CheerPostRepo cheerPostRepo;

    @MockitoBean
    private CheerReportRepo cheerReportRepo;

    @BeforeEach
    void setUp() {
        payoutTransactionRepository.deleteAll();
        paymentTransactionRepository.deleteAll();
        partyApplicationRepository.deleteAll();
        partyRepository.deleteAll();
        sellerPayoutProfileRepository.deleteAll();
        userRepository.deleteAll();

        controlledTossGateway.reset();
        ReflectionTestUtils.setField(payoutService, "payoutEnabled", true);
        ReflectionTestUtils.setField(payoutService, "payoutProvider", "TOSS");
    }

    @Test
    @DisplayName("provider=TOSS + seller 매핑 존재 시 수동 정산 요청은 COMPLETED와 providerRef를 저장한다")
    void manualPayout_withTossProviderAndSellerProfile_completesSuccessfully() {
        PaymentTransaction paymentTransaction = savePaymentTransaction(
                "MATE-TOSS-SUCCESS-ORDER-1",
                "MATE-TOSS-SUCCESS-PK-1",
                42000);
        saveSellerProfile(paymentTransaction.getSellerUserId(), "seller-toss-701");

        controlledTossGateway.configureSuccess("toss-payout-ref-701");

        PayoutTransaction result = paymentTransactionService.requestManualPayout(paymentTransaction.getId());

        assertThat(result.getStatus()).isEqualTo(SettlementStatus.COMPLETED);
        assertThat(result.getProviderRef()).isEqualTo("toss-payout-ref-701");

        PayoutTransaction savedPayout = payoutTransactionRepository
                .findTopByPaymentTransactionIdOrderByIdDesc(paymentTransaction.getId())
                .orElseThrow();
        assertThat(savedPayout.getStatus()).isEqualTo(SettlementStatus.COMPLETED);
        assertThat(savedPayout.getProviderRef()).isEqualTo("toss-payout-ref-701");

        PaymentTransaction savedPayment = paymentTransactionRepository.findById(paymentTransaction.getId()).orElseThrow();
        assertThat(savedPayment.getSettlementStatus()).isEqualTo(SettlementStatus.COMPLETED);

        PayoutGateway.PayoutRequest request = controlledTossGateway.getLastRequest();
        assertThat(request).isNotNull();
        assertThat(request.providerSellerId()).isEqualTo("seller-toss-701");
        assertThat(request.orderId()).isEqualTo("MATE-TOSS-SUCCESS-ORDER-1");
        assertThat(request.amount()).isEqualTo(42000);
    }

    @Test
    @DisplayName("provider=TOSS + seller 매핑 존재 + 게이트웨이 실패 시 failure_code와 FAILED 상태를 기록한다")
    void manualPayout_withTossProviderFailure_recordsFailureCode() {
        PaymentTransaction paymentTransaction = savePaymentTransaction(
                "MATE-TOSS-FAIL-ORDER-1",
                "MATE-TOSS-FAIL-PK-1",
                33000);
        saveSellerProfile(paymentTransaction.getSellerUserId(), "seller-toss-702");

        controlledTossGateway.configureFailure(new PayoutGateway.PayoutGatewayException(
                "invalid seller profile",
                "TOSS_SELLER_INVALID",
                HttpStatusCode.valueOf(400)));

        PayoutTransaction result = paymentTransactionService.requestManualPayout(paymentTransaction.getId());
        assertThat(result.getStatus()).isEqualTo(SettlementStatus.FAILED);
        assertThat(result.getFailureCode()).isEqualTo("TOSS_SELLER_INVALID");

        PayoutTransaction failedPayout = payoutTransactionRepository
                .findTopByPaymentTransactionIdOrderByIdDesc(paymentTransaction.getId())
                .orElseThrow();
        assertThat(failedPayout.getStatus()).isEqualTo(SettlementStatus.FAILED);
        assertThat(failedPayout.getFailureCode()).isEqualTo("TOSS_SELLER_INVALID");
        assertThat(failedPayout.getFailReason()).contains("invalid seller profile");

        PaymentTransaction savedPayment = paymentTransactionRepository.findById(paymentTransaction.getId()).orElseThrow();
        assertThat(savedPayment.getSettlementStatus()).isEqualTo(SettlementStatus.FAILED);

        verify(jobScheduler, never()).schedule(any(Instant.class), any(JobLambda.class));
    }

    @Test
    @DisplayName("provider=TOSS + seller 매핑 미존재 시 failure_code=SELLER_PROFILE_MISSING을 기록한다")
    void manualPayout_withoutSellerProfile_recordsSellerProfileMissing() {
        PaymentTransaction paymentTransaction = savePaymentTransaction(
                "MATE-TOSS-MISSING-ORDER-1",
                "MATE-TOSS-MISSING-PK-1",
                29000);

        PayoutTransaction result = paymentTransactionService.requestManualPayout(paymentTransaction.getId());
        assertThat(result.getStatus()).isEqualTo(SettlementStatus.FAILED);
        assertThat(result.getFailureCode()).isEqualTo("SELLER_PROFILE_MISSING");

        PayoutTransaction failedPayout = payoutTransactionRepository
                .findTopByPaymentTransactionIdOrderByIdDesc(paymentTransaction.getId())
                .orElseThrow();
        assertThat(failedPayout.getStatus()).isEqualTo(SettlementStatus.FAILED);
        assertThat(failedPayout.getFailureCode()).isEqualTo("SELLER_PROFILE_MISSING");

        PaymentTransaction savedPayment = paymentTransactionRepository.findById(paymentTransaction.getId()).orElseThrow();
        assertThat(savedPayment.getSettlementStatus()).isEqualTo(SettlementStatus.FAILED);
        assertThat(controlledTossGateway.getCallCount()).isEqualTo(0);

        verify(jobScheduler, never()).schedule(any(Instant.class), any(JobLambda.class));
    }

    @Test
    @DisplayName("dispatch intent 커밋 후 gateway 호출 중 만료돼도 1회 호출과 수동 대사 표식을 보존한다")
    void manualPayout_commitsDispatchIntentBeforeGatewayAndPreservesExpiryHold() {
        PaymentTransaction payment = savePaymentTransaction(
                "MATE-TOSS-EXPIRY-ORDER-1",
                "MATE-TOSS-EXPIRY-PK-1",
                31000);
        saveSellerProfile(payment.getSellerUserId(), "seller-toss-704");
        controlledTossGateway.configureSuccess("toss-payout-after-expiry-704");
        controlledTossGateway.expirePartyBeforeProviderAccepts(payment.getPartyId());

        PayoutTransaction result = paymentTransactionService.requestManualPayout(payment.getId());

        assertThat(controlledTossGateway.wasDispatchIntentCommittedBeforeRequest()).isTrue();
        assertThat(controlledTossGateway.wasTransactionActiveAtGatewayEntry()).isFalse();
        assertThat(controlledTossGateway.wasPartyExpiredBeforeProviderAccepts()).isTrue();
        assertThat(controlledTossGateway.getCallCount()).isEqualTo(1);
        assertThat(result.getProviderDispatchIntentAt()).isNotNull();
        assertThat(result.getStatus()).isEqualTo(SettlementStatus.COMPLETED);
        assertThat(result.getFailureCode()).isEqualTo("PAYOUT_MANUAL_RECONCILIATION_REQUIRED");
        assertThat(result.getNextRetryAt()).isNull();

        PaymentTransaction savedPayment = paymentTransactionRepository.findById(payment.getId()).orElseThrow();
        assertThat(savedPayment.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(savedPayment.getSettlementStatus()).isEqualTo(SettlementStatus.COMPLETED);
    }

    private PaymentTransaction savePaymentTransaction(
            String orderId,
            String paymentKey,
            int amount) {
        String sellerSuffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        UserEntity seller = userRepository.saveAndFlush(UserEntity.builder()
                .uniqueId(UUID.randomUUID())
                .handle("@payout" + sellerSuffix)
                .name("Synthetic payout seller")
                .email("payout-" + sellerSuffix + "@synthetic.test")
                .role("ROLE_USER")
                .enabled(true)
                .pendingDeletion(false)
                .build());
        Long sellerUserId = seller.getId();
        Party party = partyRepository.saveAndFlush(Party.builder()
                .hostId(sellerUserId)
                .hostName("Synthetic payout seller")
                .hostBadge(Party.BadgeType.NEW)
                .teamId("LG")
                .gameDate(LocalDate.now().plusDays(1))
                .gameTime(LocalTime.of(18, 30))
                .stadium("Synthetic stadium")
                .homeTeam("LG")
                .awayTeam("KT")
                .section("Synthetic section")
                .maxParticipants(4)
                .currentParticipants(2)
                .description("Synthetic payout integration party")
                .ticketVerified(true)
                .status(Party.PartyStatus.PENDING)
                .build());
        PartyApplication application = partyApplicationRepository.saveAndFlush(PartyApplication.builder()
                .partyId(party.getId())
                .applicantId(200L)
                .applicantName("Synthetic payout applicant")
                .applicantBadge(Party.BadgeType.NEW)
                .message("synthetic approved payout application")
                .depositAmount(amount)
                .paymentType(PartyApplication.PaymentType.FULL)
                .isPaid(true)
                .isApproved(true)
                .isRejected(false)
                .orderId(orderId)
                .paymentKey(paymentKey)
                .build());
        return paymentTransactionRepository.saveAndFlush(PaymentTransaction.builder()
                .partyId(party.getId())
                .applicationId(application.getId())
                .buyerUserId(200L)
                .sellerUserId(sellerUserId)
                .flowType(PaymentFlowType.SELLING_FULL)
                .orderId(orderId)
                .paymentKey(paymentKey)
                .grossAmount(amount)
                .feeAmount(0)
                .refundAmount(0)
                .netAmount(amount)
                .paymentStatus(PaymentStatus.PAID)
                .settlementStatus(SettlementStatus.PENDING)
                .build());
    }

    private void saveSellerProfile(Long userId, String providerSellerId) {
        sellerPayoutProfileRepository.save(SellerPayoutProfile.builder()
                .userId(userId)
                .provider("TOSS")
                .providerSellerId(providerSellerId)
                .kycStatus("APPROVED")
                .metadataJson("{}")
                .build());
    }

    @TestConfiguration
    static class TossGatewayTestConfig {
        @Bean
        ControlledTossGateway controlledTossGateway(
                PayoutTransactionRepository payoutTransactionRepository,
                PartyRepository partyRepository,
                PartyLifecycleMutationService partyLifecycleMutationService) {
            return new ControlledTossGateway(
                    payoutTransactionRepository,
                    partyRepository,
                    partyLifecycleMutationService);
        }
    }

    static class ControlledTossGateway implements PayoutGateway {
        private final PayoutTransactionRepository payoutTransactionRepository;
        private final PartyRepository partyRepository;
        private final PartyLifecycleMutationService partyLifecycleMutationService;
        private volatile PayoutResult successResult = new PayoutResult("toss-payout-default", "REQUESTED");
        private volatile RuntimeException nextFailure;
        private volatile PayoutRequest lastRequest;
        private volatile Long expirePartyIdBeforeProviderAccepts;
        private volatile boolean dispatchIntentCommittedBeforeRequest;
        private volatile boolean transactionActiveAtGatewayEntry;
        private volatile boolean partyExpiredBeforeProviderAccepts;
        private final AtomicInteger callCount = new AtomicInteger(0);

        ControlledTossGateway(
                PayoutTransactionRepository payoutTransactionRepository,
                PartyRepository partyRepository,
                PartyLifecycleMutationService partyLifecycleMutationService) {
            this.payoutTransactionRepository = payoutTransactionRepository;
            this.partyRepository = partyRepository;
            this.partyLifecycleMutationService = partyLifecycleMutationService;
        }

        @Override
        public String getProviderCode() {
            return "TOSS";
        }

        @Override
        public PayoutResult requestPayout(PayoutRequest request) {
            Long partyId = expirePartyIdBeforeProviderAccepts;
            if (partyId != null) {
                PayoutTransaction committedPayout = payoutTransactionRepository
                        .findTopByPaymentTransactionIdOrderByIdDesc(request.paymentTransactionId())
                        .orElseThrow();
                dispatchIntentCommittedBeforeRequest = committedPayout.getProviderDispatchIntentAt() != null;
                transactionActiveAtGatewayEntry = TransactionSynchronizationManager.isActualTransactionActive();
                partyLifecycleMutationService.expirePendingParty(partyId, LocalDate.now().plusDays(2));
                partyExpiredBeforeProviderAccepts = partyRepository.findById(partyId)
                        .map(expiredParty -> expiredParty.getStatus() == Party.PartyStatus.FAILED)
                        .orElse(false);
                expirePartyIdBeforeProviderAccepts = null;
            }
            lastRequest = request;
            callCount.incrementAndGet();
            RuntimeException failure = nextFailure;
            if (failure != null) {
                throw failure;
            }
            return successResult;
        }

        void configureSuccess(String providerRef) {
            this.nextFailure = null;
            this.successResult = new PayoutResult(providerRef, "COMPLETED");
        }

        void configureFailure(RuntimeException failure) {
            this.nextFailure = failure;
        }

        void expirePartyBeforeProviderAccepts(Long partyId) {
            this.expirePartyIdBeforeProviderAccepts = partyId;
            this.dispatchIntentCommittedBeforeRequest = false;
            this.transactionActiveAtGatewayEntry = true;
            this.partyExpiredBeforeProviderAccepts = false;
        }

        void reset() {
            this.successResult = new PayoutResult("toss-payout-default", "REQUESTED");
            this.nextFailure = null;
            this.lastRequest = null;
            this.expirePartyIdBeforeProviderAccepts = null;
            this.dispatchIntentCommittedBeforeRequest = false;
            this.transactionActiveAtGatewayEntry = false;
            this.partyExpiredBeforeProviderAccepts = false;
            this.callCount.set(0);
        }

        PayoutRequest getLastRequest() {
            return lastRequest;
        }

        int getCallCount() {
            return callCount.get();
        }

        boolean wasDispatchIntentCommittedBeforeRequest() {
            return dispatchIntentCommittedBeforeRequest;
        }

        boolean wasTransactionActiveAtGatewayEntry() {
            return transactionActiveAtGatewayEntry;
        }

        boolean wasPartyExpiredBeforeProviderAccepts() {
            return partyExpiredBeforeProviderAccepts;
        }
    }
}
