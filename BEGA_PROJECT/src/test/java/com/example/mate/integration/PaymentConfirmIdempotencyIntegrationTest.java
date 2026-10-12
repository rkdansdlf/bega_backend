package com.example.mate.integration;

import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserProviderRepository;
import com.example.auth.repository.UserRepository;
import com.example.cheerboard.repo.CheerPostRepo;
import com.example.cheerboard.repo.CheerReportRepo;
import com.example.mate.dto.TossPaymentDTO;
import com.example.mate.entity.Party;
import com.example.mate.entity.PaymentFlowType;
import com.example.mate.entity.PaymentStatus;
import com.example.mate.repository.CheckInRecordRepository;
import com.example.mate.repository.PartyApplicationRepository;
import com.example.mate.repository.PartyRepository;
import com.example.mate.repository.PaymentIntentRepository;
import com.example.mate.repository.PaymentTransactionRepository;
import com.example.mate.repository.PayoutTransactionRepository;
import com.example.mate.service.TossPaymentService;
import com.example.mate.service.PartyLifecycleMutationService;
import com.example.mate.support.MateTestFixtureFactory;
import com.example.mate.support.MateTestTokenHelper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
        "spring.profiles.active=test",
        "spring.jwt.secret=test-jwt-secret-64-characters-long-for-hs512-signature-tests-key-1234567890",
        "spring.jwt.refresh-expiration=86400000",
        "spring.datasource.url=jdbc:h2:mem:payment_confirm_idempotency;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;LOCK_TIMEOUT=15000",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
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
class PaymentConfirmIdempotencyIntegrationTest {

    private static final String HOST_EMAIL = "mate-host-confirm@example.com";
    private static final String APPLICANT_EMAIL = "mate-applicant-confirm@example.com";

    private record ConfirmResult(int status, String body) {}

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoSpyBean
    private UserRepository userRepository;

    @Autowired
    private UserProviderRepository userProviderRepository;

    @Autowired
    private PartyRepository partyRepository;

    @Autowired
    private PartyApplicationRepository partyApplicationRepository;

    @Autowired
    private PaymentIntentRepository paymentIntentRepository;

    @Autowired
    private PaymentTransactionRepository paymentTransactionRepository;

    @Autowired
    private PayoutTransactionRepository payoutTransactionRepository;

    @Autowired
    private PartyLifecycleMutationService partyLifecycleMutationService;

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
        payoutTransactionRepository.deleteAll();
        paymentTransactionRepository.deleteAll();
        partyApplicationRepository.deleteAll();
        paymentIntentRepository.deleteAll();
        partyRepository.deleteAll();
        userProviderRepository.deleteAll();
        userRepository.deleteAll();

        UserEntity host = userRepository.save(MateTestFixtureFactory.user(HOST_EMAIL, "Confirm Host"));
        UserEntity applicant = userRepository.save(MateTestFixtureFactory.user(APPLICANT_EMAIL, "Confirm Applicant"));
        MateTestTokenHelper.register(host);
        MateTestTokenHelper.register(applicant);
        userProviderRepository.save(MateTestFixtureFactory.socialProvider(host, "kakao"));
        userProviderRepository.save(MateTestFixtureFactory.socialProvider(applicant, "naver"));
    }

    @Test
    @DisplayName("동일 orderId/intentId confirm 동시 호출 시 신청/결제 트랜잭션은 1건만 생성된다")
    void concurrentConfirmCreatesSingleApplicationAndTransaction() throws Exception {
        UserEntity host = userRepository.findByEmail(HOST_EMAIL).orElseThrow();
        Party pendingParty = partyRepository.save(MateTestFixtureFactory.pendingParty(host.getId(), host.getName(), 2));

        JsonNode prepareNode = prepareIntent(pendingParty.getId(), PaymentFlowType.DEPOSIT);
        long intentId = prepareNode.get("intentId").asLong();
        String orderId = prepareNode.get("orderId").asText();
        int amount = prepareNode.get("amount").asInt();

        CountDownLatch providerConfirmationStarted = new CountDownLatch(1);
        CountDownLatch allowProviderConfirmationToComplete = new CountDownLatch(1);
        given(tossPaymentService.confirmPayment(eq("race-pay-key"), eq(orderId), eq(amount)))
                .willAnswer(invocation -> {
                    providerConfirmationStarted.countDown();
                    if (!allowProviderConfirmationToComplete.await(10, TimeUnit.SECONDS)) {
                        throw new AssertionError("Synthetic provider confirmation was not released");
                    }
                    return new TossPaymentDTO.ConfirmResponse(
                            "race-pay-key",
                            orderId,
                            "DONE",
                            amount,
                            "카드");
                });

        String confirmBody = objectMapper.writeValueAsString(Map.of(
                "paymentKey", "race-pay-key",
                "orderId", orderId,
                "intentId", intentId,
                "partyId", pendingParty.getId(),
                "flowType", PaymentFlowType.DEPOSIT.name(),
                "message", "동시 confirm"));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        Callable<ConfirmResult> confirmTask = () -> {
            var result = mockMvc.perform(post("/api/payments/toss/confirm")
                            .with(MateTestTokenHelper.principalAs(APPLICANT_EMAIL))
                            .contentType("application/json")
                            .content(confirmBody))
                    .andReturn();
            return new ConfirmResult(
                    result.getResponse().getStatus(), result.getResponse().getContentAsString());
        };

        Future<ConfirmResult> first = executor.submit(confirmTask);
        assertThat(providerConfirmationStarted.await(10, TimeUnit.SECONDS)).isTrue();
        CountDownLatch secondRequestStarted = new CountDownLatch(1);
        Future<ConfirmResult> second = executor.submit(() -> {
            secondRequestStarted.countDown();
            return confirmTask.call();
        });
        assertThat(secondRequestStarted.await(5, TimeUnit.SECONDS)).isTrue();
        boolean secondWaitedForFirstConfirmation = !second.isDone();
        allowProviderConfirmationToComplete.countDown();
        assertThat(secondWaitedForFirstConfirmation).isTrue();
        ConfirmResult responseA = first.get(20, TimeUnit.SECONDS);
        ConfirmResult responseB = second.get(20, TimeUnit.SECONDS);
        executor.shutdownNow();

        assertThat(List.of(responseA.status(), responseB.status()))
                .as("response bodies: %s | %s", responseA.body(), responseB.body())
                .containsExactlyInAnyOrder(200, 201);
        assertThat(partyApplicationRepository.countByOrderId(orderId)).isEqualTo(1L);
        assertThat(paymentTransactionRepository.findByOrderId(orderId)).isPresent();
        verify(tossPaymentService, times(1)).confirmPayment("race-pay-key", orderId, amount);
        verify(tossPaymentService, never()).getPayment(anyString());
    }

    @Test
    @DisplayName("서로 다른 결제 요청도 같은 신청자 잠금으로 provider 승인 구간이 직렬화된다")
    void concurrentConfirmForDifferentIntentsBySameApplicantSerializesProviderCalls() throws Exception {
        UserEntity host = userRepository.findByEmail(HOST_EMAIL).orElseThrow();
        Party firstParty = partyRepository.save(MateTestFixtureFactory.pendingParty(host.getId(), host.getName(), 2));
        Party secondParty = partyRepository.save(MateTestFixtureFactory.pendingParty(host.getId(), host.getName(), 2));
        JsonNode firstPrepared = prepareIntent(firstParty.getId(), PaymentFlowType.DEPOSIT);
        JsonNode secondPrepared = prepareIntent(secondParty.getId(), PaymentFlowType.DEPOSIT);
        String firstOrderId = firstPrepared.get("orderId").asText();
        String secondOrderId = secondPrepared.get("orderId").asText();
        int firstAmount = firstPrepared.get("amount").asInt();
        int secondAmount = secondPrepared.get("amount").asInt();
        String firstPaymentKey = "serialized-first-pay-key";
        String secondPaymentKey = "serialized-second-pay-key";

        CountDownLatch firstProviderStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstProvider = new CountDownLatch(1);
        CountDownLatch secondProviderStarted = new CountDownLatch(1);
        given(tossPaymentService.confirmPayment(eq(firstPaymentKey), eq(firstOrderId), eq(firstAmount)))
                .willAnswer(invocation -> {
                    firstProviderStarted.countDown();
                    if (!releaseFirstProvider.await(10, TimeUnit.SECONDS)) {
                        throw new AssertionError("Synthetic first provider confirmation was not released");
                    }
                    return new TossPaymentDTO.ConfirmResponse(
                            firstPaymentKey, firstOrderId, "DONE", firstAmount, "카드");
                });
        given(tossPaymentService.confirmPayment(eq(secondPaymentKey), eq(secondOrderId), eq(secondAmount)))
                .willAnswer(invocation -> {
                    secondProviderStarted.countDown();
                    return new TossPaymentDTO.ConfirmResponse(
                            secondPaymentKey, secondOrderId, "DONE", secondAmount, "카드");
                });

        String firstBody = confirmationBody(firstPrepared, firstParty.getId(), firstPaymentKey);
        String secondBody = confirmationBody(secondPrepared, secondParty.getId(), secondPaymentKey);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<ConfirmResult> firstRequest = executor.submit(() -> submitConfirmation(firstBody));
            assertThat(firstProviderStarted.await(10, TimeUnit.SECONDS)).isTrue();
            verify(userRepository, timeout(5_000).times(1)).lockPaymentConfirmationOwnerForWrite(anyLong());

            CountDownLatch secondRequestStarted = new CountDownLatch(1);
            Future<ConfirmResult> secondRequest = executor.submit(() -> {
                secondRequestStarted.countDown();
                return submitConfirmation(secondBody);
            });
            assertThat(secondRequestStarted.await(5, TimeUnit.SECONDS)).isTrue();
            verify(userRepository, timeout(5_000).times(2)).lockPaymentConfirmationOwnerForWrite(anyLong());
            boolean secondProviderStartedBeforeRelease = secondProviderStarted.await(250, TimeUnit.MILLISECONDS);

            releaseFirstProvider.countDown();
            assertThat(secondProviderStartedBeforeRelease).isFalse();
            assertThat(secondProviderStarted.await(10, TimeUnit.SECONDS)).isTrue();

            ConfirmResult firstResponse = firstRequest.get(20, TimeUnit.SECONDS);
            ConfirmResult secondResponse = secondRequest.get(20, TimeUnit.SECONDS);
            assertThat(List.of(firstResponse.status(), secondResponse.status()))
                    .as("response bodies: %s | %s", firstResponse.body(), secondResponse.body())
                    .containsExactly(201, 201);
            assertThat(partyApplicationRepository.countByOrderId(firstOrderId)).isEqualTo(1L);
            assertThat(partyApplicationRepository.countByOrderId(secondOrderId)).isEqualTo(1L);
            verify(tossPaymentService, times(1)).confirmPayment(firstPaymentKey, firstOrderId, firstAmount);
            verify(tossPaymentService, times(1)).confirmPayment(secondPaymentKey, secondOrderId, secondAmount);
            verify(tossPaymentService, never()).getPayment(anyString());
        } finally {
            releaseFirstProvider.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("결제 승인 중 파티 만료가 먼저 커밋되면 유료 신청 생성을 거부하고 기존 보상 취소를 수행한다")
    void partyExpiryDuringProviderConfirmationPreventsPaidApplicationAndCompensatesPayment() throws Exception {
        UserEntity host = userRepository.findByEmail(HOST_EMAIL).orElseThrow();
        Party pendingParty = MateTestFixtureFactory.pendingParty(host.getId(), host.getName(), 2);
        pendingParty.setGameDate(LocalDate.now().minusDays(1));
        pendingParty = partyRepository.save(pendingParty);
        Long partyId = pendingParty.getId();

        JsonNode prepared = prepareIntent(partyId, PaymentFlowType.DEPOSIT);
        String orderId = prepared.get("orderId").asText();
        String paymentKey = "party-expiry-race-payment";
        int amount = prepared.get("amount").asInt();
        CountDownLatch providerStarted = new CountDownLatch(1);
        CountDownLatch releaseProvider = new CountDownLatch(1);
        given(tossPaymentService.confirmPayment(eq(paymentKey), eq(orderId), eq(amount)))
                .willAnswer(invocation -> {
                    providerStarted.countDown();
                    if (!releaseProvider.await(10, TimeUnit.SECONDS)) {
                        throw new AssertionError("Synthetic provider confirmation was not released");
                    }
                    return new TossPaymentDTO.ConfirmResponse(
                            paymentKey, orderId, "DONE", amount, "카드");
                });
        TossPaymentDTO.CancelResponse cancellation = new TossPaymentDTO.CancelResponse(
                paymentKey, "CANCELED", amount);
        cancellation.setCancels(List.of(new TossPaymentDTO.CancelDetail(
                amount, "DONE", "synthetic-cancel-time", "synthetic-cancel-transaction")));
        given(tossPaymentService.cancelPayment(eq(paymentKey), anyString(), eq(amount)))
                .willReturn(cancellation);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<ConfirmResult> confirmation = executor.submit(
                    () -> submitConfirmation(confirmationBody(prepared, partyId, paymentKey)));
            assertThat(providerStarted.await(10, TimeUnit.SECONDS)).isTrue();

            assertThat(partyLifecycleMutationService.expirePendingParty(
                    partyId, LocalDate.now())).isNotNull();
            assertThat(partyRepository.findById(partyId).orElseThrow().getStatus())
                    .isEqualTo(Party.PartyStatus.FAILED);

            releaseProvider.countDown();
            ConfirmResult result = confirmation.get(20, TimeUnit.SECONDS);

            assertThat(result.status()).isNotIn(200, 201);
            assertThat(partyApplicationRepository.countByOrderId(orderId)).isZero();
            assertThat(paymentTransactionRepository.findByOrderId(orderId)).isEmpty();
            assertThat(paymentIntentRepository.findByOrderId(orderId).orElseThrow().getStatus())
                    .isEqualTo(com.example.mate.entity.PaymentIntent.IntentStatus.CANCELED);
            verify(tossPaymentService).confirmPayment(paymentKey, orderId, amount);
            verify(tossPaymentService).cancelPayment(eq(paymentKey), anyString(), eq(amount));
        } finally {
            releaseProvider.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("SELLING_FULL 승인 중 파티가 판매 완료되면 자동승인 신청을 막고 기존 보상 취소를 수행한다")
    void sellingPartyBecomesSoldDuringProviderConfirmationPreventsPaidApplicationAndCompensatesPayment()
            throws Exception {
        UserEntity host = userRepository.findByEmail(HOST_EMAIL).orElseThrow();
        Party sellingParty = partyRepository.save(
                MateTestFixtureFactory.sellingParty(host.getId(), host.getName(), 2, 50000));
        Long partyId = sellingParty.getId();

        JsonNode prepared = prepareIntent(partyId, PaymentFlowType.SELLING_FULL);
        String orderId = prepared.get("orderId").asText();
        String paymentKey = "selling-party-closed-race-payment";
        int amount = prepared.get("amount").asInt();
        CountDownLatch providerStarted = new CountDownLatch(1);
        CountDownLatch releaseProvider = new CountDownLatch(1);
        given(tossPaymentService.confirmPayment(eq(paymentKey), eq(orderId), eq(amount)))
                .willAnswer(invocation -> {
                    providerStarted.countDown();
                    if (!releaseProvider.await(10, TimeUnit.SECONDS)) {
                        throw new AssertionError("Synthetic provider confirmation was not released");
                    }
                    return new TossPaymentDTO.ConfirmResponse(
                            paymentKey, orderId, "DONE", amount, "카드");
                });
        TossPaymentDTO.CancelResponse cancellation = new TossPaymentDTO.CancelResponse(
                paymentKey, "CANCELED", amount);
        cancellation.setCancels(List.of(new TossPaymentDTO.CancelDetail(
                amount, "DONE", "synthetic-cancel-time", "synthetic-cancel-transaction")));
        given(tossPaymentService.cancelPayment(eq(paymentKey), anyString(), eq(amount)))
                .willReturn(cancellation);

        String confirmBody = objectMapper.writeValueAsString(Map.of(
                "paymentKey", paymentKey,
                "orderId", orderId,
                "intentId", prepared.get("intentId").asLong(),
                "partyId", partyId,
                "flowType", PaymentFlowType.SELLING_FULL.name(),
                "paymentType", "FULL",
                "message", "판매 종료 중인 전액결제 race"));
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<ConfirmResult> confirmation = executor.submit(() -> submitConfirmation(confirmBody));
            assertThat(providerStarted.await(10, TimeUnit.SECONDS)).isTrue();

            Party closedParty = partyRepository.findById(partyId).orElseThrow();
            closedParty.setStatus(Party.PartyStatus.SOLD);
            partyRepository.save(closedParty);
            Party committedParty = partyRepository.findById(partyId).orElseThrow();
            assertThat(committedParty.getStatus()).isEqualTo(Party.PartyStatus.SOLD);
            assertThat(committedParty.getCurrentParticipants()).isEqualTo(1);

            releaseProvider.countDown();
            ConfirmResult result = confirmation.get(20, TimeUnit.SECONDS);

            assertThat(result.status()).isNotIn(200, 201);
            assertThat(partyApplicationRepository.countByOrderId(orderId)).isZero();
            assertThat(paymentTransactionRepository.findByOrderId(orderId)).isEmpty();
            assertThat(paymentIntentRepository.findByOrderId(orderId).orElseThrow().getStatus())
                    .isEqualTo(com.example.mate.entity.PaymentIntent.IntentStatus.CANCELED);
            Party finalParty = partyRepository.findById(partyId).orElseThrow();
            assertThat(finalParty.getCurrentParticipants()).isEqualTo(1);
            assertThat(finalParty.getStatus()).isEqualTo(Party.PartyStatus.SOLD);
            verify(tossPaymentService).confirmPayment(paymentKey, orderId, amount);
            verify(tossPaymentService).cancelPayment(eq(paymentKey), anyString(), eq(amount));
        } finally {
            releaseProvider.countDown();
            executor.shutdownNow();
        }
    }

    private ConfirmResult submitConfirmation(String body) throws Exception {
        var result = mockMvc.perform(post("/api/payments/toss/confirm")
                        .with(MateTestTokenHelper.principalAs(APPLICANT_EMAIL))
                        .contentType("application/json")
                        .content(body))
                .andReturn();
        return new ConfirmResult(result.getResponse().getStatus(), result.getResponse().getContentAsString());
    }

    private String confirmationBody(JsonNode prepared, Long partyId, String paymentKey) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "paymentKey", paymentKey,
                "orderId", prepared.get("orderId").asText(),
                "intentId", prepared.get("intentId").asLong(),
                "partyId", partyId,
                "flowType", PaymentFlowType.DEPOSIT.name(),
                "message", "동시 confirm"));
    }

    @Test
    @DisplayName("confirm 시 flowType/paymentType 조합이 intent와 다르면 409를 반환한다")
    void confirmRejectsFlowTypeAndPaymentTypeMismatch() throws Exception {
        UserEntity host = userRepository.findByEmail(HOST_EMAIL).orElseThrow();
        Party pendingParty = partyRepository.save(MateTestFixtureFactory.pendingParty(host.getId(), host.getName(), 2));

        JsonNode prepareNode = prepareIntent(pendingParty.getId(), PaymentFlowType.DEPOSIT);
        long intentId = prepareNode.get("intentId").asLong();
        String orderId = prepareNode.get("orderId").asText();

        String confirmBody = objectMapper.writeValueAsString(Map.of(
                "paymentKey", "mismatch-pay-key",
                "orderId", orderId,
                "intentId", intentId,
                "partyId", pendingParty.getId(),
                "flowType", PaymentFlowType.SELLING_FULL.name(),
                "paymentType", "FULL",
                "message", "mismatch"));

        int statusCode = mockMvc.perform(post("/api/payments/toss/confirm")
                        .with(MateTestTokenHelper.principalAs(APPLICANT_EMAIL))
                        .contentType("application/json")
                        .content(confirmBody))
                .andReturn()
                .getResponse()
                .getStatus();

        assertThat(statusCode).isIn(400, 409);

        verify(tossPaymentService, never()).confirmPayment(anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("SELLING_FULL confirm 성공 시 자동승인/결제트랜잭션이 생성되고 재호출은 기존 신청을 재사용한다")
    void sellingFullConfirmCreatesPaidApprovedApplicationAndReusesOnRetry() throws Exception {
        UserEntity host = userRepository.findByEmail(HOST_EMAIL).orElseThrow();
        Party sellingParty = partyRepository.save(MateTestFixtureFactory.sellingParty(host.getId(), host.getName(), 2, 50000));

        JsonNode prepareNode = prepareIntent(sellingParty.getId(), PaymentFlowType.SELLING_FULL);
        long intentId = prepareNode.get("intentId").asLong();
        String orderId = prepareNode.get("orderId").asText();
        int amount = prepareNode.get("amount").asInt();
        assertThat(amount).isEqualTo(50000);

        given(tossPaymentService.confirmPayment(eq("selling-pay-key"), eq(orderId), eq(amount)))
                .willReturn(new TossPaymentDTO.ConfirmResponse(
                        "selling-pay-key",
                        orderId,
                        "DONE",
                        amount,
                        "카드"));

        String confirmBody = objectMapper.writeValueAsString(Map.of(
                "paymentKey", "selling-pay-key",
                "orderId", orderId,
                "intentId", intentId,
                "partyId", sellingParty.getId(),
                "flowType", PaymentFlowType.SELLING_FULL.name(),
                "paymentType", "FULL",
                "message", "selling full confirm"));

        String firstConfirmJson = mockMvc.perform(post("/api/payments/toss/confirm")
                        .with(MateTestTokenHelper.principalAs(APPLICANT_EMAIL))
                        .contentType("application/json")
                        .content(confirmBody))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode firstNode = objectMapper.readTree(firstConfirmJson);
        long applicationId = firstNode.get("id").asLong();
        assertThat(firstNode.get("paymentType").asText()).isEqualTo("FULL");
        assertThat(firstNode.get("isPaid").asBoolean()).isTrue();
        assertThat(firstNode.get("isApproved").asBoolean()).isTrue();

        var secondResult = mockMvc.perform(post("/api/payments/toss/confirm")
                        .with(MateTestTokenHelper.principalAs(APPLICANT_EMAIL))
                        .contentType("application/json")
                        .content(confirmBody))
                .andReturn();
        assertThat(secondResult.getResponse().getStatus()).isIn(200, 201);

        String secondConfirmJson = secondResult.getResponse().getContentAsString();
        JsonNode secondNode = objectMapper.readTree(secondConfirmJson);
        assertThat(secondNode.get("id").asLong()).isEqualTo(applicationId);

        assertThat(partyApplicationRepository.countByOrderId(orderId)).isEqualTo(1L);

        Party updatedParty = partyRepository.findById(sellingParty.getId()).orElseThrow();
        assertThat(updatedParty.getCurrentParticipants()).isEqualTo(2);
        assertThat(updatedParty.getStatus()).isEqualTo(Party.PartyStatus.MATCHED);

        var transaction = paymentTransactionRepository.findByOrderId(orderId).orElseThrow();
        assertThat(transaction.getFlowType()).isEqualTo(PaymentFlowType.SELLING_FULL);
        assertThat(transaction.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(transaction.getOrderId()).isEqualTo(orderId);

        verify(tossPaymentService, times(1)).confirmPayment(eq("selling-pay-key"), eq(orderId), eq(amount));
    }

    private JsonNode prepareIntent(Long partyId, PaymentFlowType flowType) throws Exception {
        String prepareBody = objectMapper.writeValueAsString(Map.of(
                "partyId", partyId,
                "flowType", flowType.name()));
        String prepareJson = mockMvc.perform(post("/api/payments/toss/prepare")
                        .with(MateTestTokenHelper.principalAs(APPLICANT_EMAIL))
                        .contentType("application/json")
                        .content(prepareBody))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(prepareJson);
    }
}
