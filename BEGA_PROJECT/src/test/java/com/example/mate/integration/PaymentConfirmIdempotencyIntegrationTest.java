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
import com.example.mate.support.MateTestFixtureFactory;
import com.example.mate.support.MateTestTokenHelper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
        "spring.profiles.active=test",
        "spring.jwt.secret=test-jwt-secret-32-characters-long",
        "spring.jwt.refresh-expiration=86400000",
        "spring.datasource.url=jdbc:h2:mem:payment_confirm_idempotency;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
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
        "payment.payout.enabled=false",
        "mate.payment.mode=TOSS_TEST",
        "storage.type=oci",
        "oci.s3.endpoint=http://localhost:4566",
        "oci.s3.access-key=test-access-key",
        "oci.s3.secret-key=test-secret-key",
        "oci.s3.bucket=test-bucket",
        "oci.s3.region=ap-seoul-1",
        "spring.autoconfigure.exclude=io.awspring.cloud.autoconfigure.s3.S3AutoConfiguration"
})
class PaymentConfirmIdempotencyIntegrationTest {

    private static final String HOST_EMAIL = "mate-host-confirm@example.com";
    private static final String APPLICANT_EMAIL = "mate-applicant-confirm@example.com";

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
    private PartyApplicationRepository partyApplicationRepository;

    @Autowired
    private PaymentIntentRepository paymentIntentRepository;

    @Autowired
    private PaymentTransactionRepository paymentTransactionRepository;

    @Autowired
    private PayoutTransactionRepository payoutTransactionRepository;

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

        given(tossPaymentService.confirmPayment(eq("race-pay-key"), eq(orderId), eq(amount)))
                .willAnswer(invocation -> {
                    Thread.sleep(Duration.ofMillis(150));
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

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Callable<Integer> confirmTask = () -> {
            start.await(5, TimeUnit.SECONDS);
            return mockMvc.perform(post("/api/payments/toss/confirm")
                            .with(MateTestTokenHelper.principalAs(APPLICANT_EMAIL))
                            .contentType("application/json")
                            .content(confirmBody))
                    .andReturn()
                    .getResponse()
                    .getStatus();
        };

        Future<Integer> first = executor.submit(confirmTask);
        Future<Integer> second = executor.submit(confirmTask);
        start.countDown();

        int statusA = first.get(20, TimeUnit.SECONDS);
        int statusB = second.get(20, TimeUnit.SECONDS);
        executor.shutdownNow();

        assertThat(List.of(statusA, statusB)).containsExactlyInAnyOrder(200, 201);
        assertThat(partyApplicationRepository.countByOrderId(orderId)).isEqualTo(1L);
        assertThat(paymentTransactionRepository.findByOrderId(orderId)).isPresent();
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
