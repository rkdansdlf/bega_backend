package com.example.mate.integration;

import com.example.cheerboard.repo.CheerPostRepo;
import com.example.cheerboard.repo.CheerReportRepo;
import com.example.mate.entity.PaymentFlowType;
import com.example.mate.entity.PaymentStatus;
import com.example.mate.entity.PaymentTransaction;
import com.example.mate.entity.PayoutTransaction;
import com.example.mate.entity.SellerPayoutProfile;
import com.example.mate.entity.SettlementStatus;
import com.example.mate.repository.PaymentTransactionRepository;
import com.example.mate.repository.PayoutTransactionRepository;
import com.example.mate.repository.SellerPayoutProfileRepository;
import com.example.mate.service.PaymentTransactionService;
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

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.profiles.active=test",
        "spring.jwt.secret=test-jwt-secret-32-characters-long",
        "spring.jwt.refresh-expiration=86400000",
        "spring.datasource.url=jdbc:h2:mem:mate_payout_toss_integration;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.jpa.open-in-view=false",
        "spring.data.redis.host=127.0.0.1",
        "spring.data.redis.port=6379",
        "spring.data.redis.repositories.enabled=false",
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
        "oci.s3.region=ap-seoul-1",
        "spring.autoconfigure.exclude=io.awspring.cloud.autoconfigure.s3.S3AutoConfiguration"
})
class PayoutTossProviderIntegrationTest {

    @Autowired
    private PaymentTransactionService paymentTransactionService;

    @Autowired
    private PayoutService payoutService;

    @Autowired
    private PaymentTransactionRepository paymentTransactionRepository;

    @Autowired
    private PayoutTransactionRepository payoutTransactionRepository;

    @Autowired
    private SellerPayoutProfileRepository sellerPayoutProfileRepository;

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
        sellerPayoutProfileRepository.deleteAll();

        controlledTossGateway.reset();
        ReflectionTestUtils.setField(payoutService, "payoutEnabled", true);
        ReflectionTestUtils.setField(payoutService, "payoutProvider", "TOSS");
    }

    @Test
    @DisplayName("provider=TOSS + seller 매핑 존재 시 수동 정산 요청은 COMPLETED와 providerRef를 저장한다")
    void manualPayout_withTossProviderAndSellerProfile_completesSuccessfully() {
        PaymentTransaction paymentTransaction = savePaymentTransaction(
                701L,
                "MATE-TOSS-SUCCESS-ORDER-1",
                "MATE-TOSS-SUCCESS-PK-1",
                42000);
        saveSellerProfile(701L, "seller-toss-701");

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
                702L,
                "MATE-TOSS-FAIL-ORDER-1",
                "MATE-TOSS-FAIL-PK-1",
                33000);
        saveSellerProfile(702L, "seller-toss-702");

        controlledTossGateway.configureFailure(new PayoutGateway.PayoutGatewayException(
                "toss gateway down",
                "TOSS_GATEWAY_DOWN",
                HttpStatusCode.valueOf(502)));

        PayoutTransaction result = paymentTransactionService.requestManualPayout(paymentTransaction.getId());
        assertThat(result.getStatus()).isEqualTo(SettlementStatus.FAILED);
        assertThat(result.getFailureCode()).isEqualTo("TOSS_GATEWAY_DOWN");

        PayoutTransaction failedPayout = payoutTransactionRepository
                .findTopByPaymentTransactionIdOrderByIdDesc(paymentTransaction.getId())
                .orElseThrow();
        assertThat(failedPayout.getStatus()).isEqualTo(SettlementStatus.FAILED);
        assertThat(failedPayout.getFailureCode()).isEqualTo("TOSS_GATEWAY_DOWN");
        assertThat(failedPayout.getFailReason()).contains("toss gateway down");

        PaymentTransaction savedPayment = paymentTransactionRepository.findById(paymentTransaction.getId()).orElseThrow();
        assertThat(savedPayment.getSettlementStatus()).isEqualTo(SettlementStatus.FAILED);

        verify(jobScheduler).schedule(any(Instant.class), any(JobLambda.class));
    }

    @Test
    @DisplayName("provider=TOSS + seller 매핑 미존재 시 failure_code=SELLER_PROFILE_MISSING을 기록한다")
    void manualPayout_withoutSellerProfile_recordsSellerProfileMissing() {
        PaymentTransaction paymentTransaction = savePaymentTransaction(
                703L,
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

    private PaymentTransaction savePaymentTransaction(
            Long sellerUserId,
            String orderId,
            String paymentKey,
            int amount) {
        return paymentTransactionRepository.save(PaymentTransaction.builder()
                .partyId(1L)
                .applicationId(Math.abs(orderId.hashCode()) + 1000L)
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
        ControlledTossGateway controlledTossGateway() {
            return new ControlledTossGateway();
        }
    }

    static class ControlledTossGateway implements PayoutGateway {
        private volatile PayoutResult successResult = new PayoutResult("toss-payout-default", "REQUESTED");
        private volatile RuntimeException nextFailure;
        private volatile PayoutRequest lastRequest;
        private final AtomicInteger callCount = new AtomicInteger(0);

        @Override
        public String getProviderCode() {
            return "TOSS";
        }

        @Override
        public PayoutResult requestPayout(PayoutRequest request) {
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
            this.successResult = new PayoutResult(providerRef, "REQUESTED");
        }

        void configureFailure(RuntimeException failure) {
            this.nextFailure = failure;
        }

        void reset() {
            this.successResult = new PayoutResult("toss-payout-default", "REQUESTED");
            this.nextFailure = null;
            this.lastRequest = null;
            this.callCount.set(0);
        }

        PayoutRequest getLastRequest() {
            return lastRequest;
        }

        int getCallCount() {
            return callCount.get();
        }
    }
}
