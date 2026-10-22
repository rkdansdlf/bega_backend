package com.example.mate.integration;

import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserProviderRepository;
import com.example.auth.repository.UserRepository;
import com.example.cheerboard.repo.CheerPostRepo;
import com.example.cheerboard.repo.CheerReportRepo;
import com.example.mate.dto.PartyApplicationDTO;
import com.example.mate.dto.TossPaymentDTO;
import com.example.mate.entity.Party;
import com.example.mate.entity.PartyApplication;
import com.example.mate.entity.PaymentFlowType;
import com.example.mate.entity.PaymentIntent;
import com.example.mate.repository.PartyApplicationRepository;
import com.example.mate.repository.PartyRepository;
import com.example.mate.repository.PaymentIntentRepository;
import com.example.mate.repository.PaymentTransactionRepository;
import com.example.mate.service.PartyApplicationService;
import com.example.mate.service.PaymentIntentService;
import com.example.mate.service.TossPaymentService;
import com.example.mate.support.MateTestFixtureFactory;
import com.example.mate.support.MateTestTokenHelper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
        "spring.profiles.active=test",
        "spring.jwt.secret=test-jwt-secret-64-characters-long-for-hs512-signature-tests-key-1234567890",
        "spring.jwt.refresh-expiration=86400000",
        "spring.datasource.url=jdbc:h2:mem:payment_confirmation_lock_order;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;LOCK_TIMEOUT=2000",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.hikari.maximum-pool-size=10",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.jpa.open-in-view=false",
        "jobrunr.background-job-server.enabled=false",
        "jobrunr.dashboard.enabled=false",
        "payment.payout.enabled=false",
        "mate.payment.mode=TOSS_TEST",
        "storage.type=oci",
        "oci.s3.endpoint=http://localhost:4566",
        "oci.s3.access-key=test-access-key",
        "oci.s3.secret-key=test-secret-key",
        "oci.s3.bucket=test-bucket",
        "oci.s3.region=ap-seoul-1"
})
class PaymentConfirmationLockOrderIntegrationTest {

    private static final String HOST_EMAIL = "lock-order-host@example.com";
    private static final String APPLICANT_EMAIL = "lock-order-applicant@example.com";

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

    @Autowired
    private PaymentIntentRepository paymentIntentRepository;

    @Autowired
    private PartyApplicationRepository partyApplicationRepository;

    @Autowired
    private PaymentTransactionRepository paymentTransactionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private PaymentIntentService paymentIntentService;

    @Autowired
    private PartyApplicationService partyApplicationService;

    @MockitoBean
    private TossPaymentService tossPaymentService;

    @MockitoBean
    private CheerPostRepo cheerPostRepo;

    @MockitoBean
    private CheerReportRepo cheerReportRepo;

    @BeforeEach
    void setUp() {
        partyApplicationRepository.deleteAll();
        paymentTransactionRepository.deleteAll();
        paymentIntentRepository.deleteAll();
        partyRepository.deleteAll();
        userProviderRepository.deleteAll();
        userRepository.deleteAll();

        UserEntity host = userRepository.save(MateTestFixtureFactory.user(HOST_EMAIL, "Lock Order Host"));
        UserEntity applicant = userRepository.save(MateTestFixtureFactory.user(APPLICANT_EMAIL, "Lock Order Applicant"));
        MateTestTokenHelper.register(host);
        MateTestTokenHelper.register(applicant);
        userProviderRepository.save(MateTestFixtureFactory.socialProvider(host, "kakao"));
        userProviderRepository.save(MateTestFixtureFactory.socialProvider(applicant, "naver"));
    }

    @Test
    void concurrentApplicationCreationAndConfirmationRetryUseOneLockOrder() throws Exception {
        UserEntity host = userRepository.findByEmail(HOST_EMAIL).orElseThrow();
        UserEntity applicant = userRepository.findByEmail(APPLICANT_EMAIL).orElseThrow();
        Party party = partyRepository.save(MateTestFixtureFactory.pendingParty(host.getId(), host.getName(), 2));
        JsonNode prepared = prepareIntent(party.getId());
        Long intentId = prepared.get("intentId").asLong();
        String orderId = prepared.get("orderId").asText();
        String paymentKey = "lock-order-pay-key";
        int amount = prepared.get("amount").asInt();

        PaymentIntent intent = paymentIntentRepository.findById(intentId).orElseThrow();
        intent.setStatus(PaymentIntent.IntentStatus.CONFIRMED);
        intent.setPaymentKey(paymentKey);
        paymentIntentRepository.saveAndFlush(intent);

        CountDownLatch applicationLocksAcquired = new CountDownLatch(1);
        CountDownLatch allowApplicationToContinue = new CountDownLatch(1);
        CountDownLatch retryTransactionStarted = new CountDownLatch(1);
        CountDownLatch retryApplicantLockAcquired = new CountDownLatch(1);
        CountDownLatch retryAttemptedIntentReservation = new CountDownLatch(1);
        AtomicInteger applicationSessionId = new AtomicInteger(-1);
        AtomicInteger retrySessionId = new AtomicInteger(-1);

        PartyApplicationDTO.Request applicationRequest = PartyApplicationDTO.Request.builder()
                .partyId(party.getId())
                .message("lock order regression")
                .depositAmount(amount)
                .paymentType(PartyApplication.PaymentType.DEPOSIT)
                .build();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<PartyApplicationService.PaymentCreationResult> applicationCreation = executor.submit(() ->
                    paymentIntentService.withConfirmedIntentForApplicationCreation(
                            intentId,
                            applicant.getId(),
                            paymentKey,
                            lockedIntent -> {
                                applicationSessionId.set(sessionId());
                                applicationLocksAcquired.countDown();
                                await(allowApplicationToContinue);
                                return partyApplicationService.createOrGetApplicationWithPayment(
                                        applicationRequest,
                                        applicant.getId(),
                                        paymentKey,
                                        orderId,
                                        lockedIntent.getExpectedAmount(),
                                        lockedIntent.getPaymentType());
                            }));

            assertThat(applicationLocksAcquired.await(5, TimeUnit.SECONDS)).isTrue();

            Future<Boolean> confirmationRetry = executor.submit(() -> new TransactionTemplate(transactionManager)
                    .execute(status -> {
                        retrySessionId.set(sessionId());
                        retryTransactionStarted.countDown();
                        return paymentIntentService.withApplicantConfirmationSerialized(applicant.getId(), () -> {
                            retryApplicantLockAcquired.countDown();
                            retryAttemptedIntentReservation.countDown();
                            return paymentIntentService.reserveConfirmationAttempt(
                                    intentId, applicant.getId(), paymentKey);
                        });
                    }));

            assertThat(retryTransactionStarted.await(5, TimeUnit.SECONDS)).isTrue();
            awaitSessionBlockedBy(retrySessionId.get(), applicationSessionId.get(), 1);
            allowApplicationToContinue.countDown();

            PartyApplicationService.PaymentCreationResult created = applicationCreation.get(8, TimeUnit.SECONDS);
            Boolean retryReserved = confirmationRetry.get(8, TimeUnit.SECONDS);
            assertThat(retryApplicantLockAcquired.getCount()).isZero();
            assertThat(retryAttemptedIntentReservation.getCount()).isZero();
            assertThat(created.created()).isTrue();
            assertThat(retryReserved).isFalse();
            assertThat(partyApplicationRepository.countByOrderId(orderId)).isEqualTo(1L);
            assertThat(paymentTransactionRepository.findByOrderId(orderId)).isEmpty();
        } finally {
            allowApplicationToContinue.countDown();
            executor.shutdownNow();
        }
    }

    private JsonNode prepareIntent(Long partyId) throws Exception {
        String requestBody = objectMapper.writeValueAsString(Map.of(
                "partyId", partyId,
                "flowType", PaymentFlowType.DEPOSIT.name()));
        String responseBody = mockMvc.perform(post("/api/payments/toss/prepare")
                        .with(MateTestTokenHelper.principalAs(APPLICANT_EMAIL))
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(responseBody);
    }

    private int sessionId() {
        return jdbcTemplate.queryForObject("SELECT SESSION_ID()", Integer.class);
    }

    private void awaitSessionBlockedBy(int waitingSessionId, int blockerSessionId, long timeoutSeconds)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        while (System.nanoTime() < deadline) {
            Integer blocked = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM INFORMATION_SCHEMA.SESSIONS
                    WHERE SESSION_ID = ? AND BLOCKER_ID = ?
                    """, Integer.class, waitingSessionId, blockerSessionId);
            if (blocked != null && blocked > 0) {
                return;
            }
            Thread.sleep(5);
        }
        throw new AssertionError("Expected H2 session " + waitingSessionId + " to wait on " + blockerSessionId);
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for the lock-order test to continue");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Lock-order test was interrupted", exception);
        }
    }
}
