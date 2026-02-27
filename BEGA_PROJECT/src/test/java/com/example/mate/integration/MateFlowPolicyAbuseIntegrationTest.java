package com.example.mate.integration;

import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserProviderRepository;
import com.example.auth.repository.UserRepository;
import com.example.cheerboard.repo.CheerPostRepo;
import com.example.cheerboard.repo.CheerReportRepo;
import com.example.mate.dto.TossPaymentDTO;
import com.example.mate.entity.Party;
import com.example.mate.entity.PartyApplication;
import com.example.mate.entity.PaymentFlowType;
import com.example.mate.repository.CheckInRecordRepository;
import com.example.mate.repository.PartyApplicationRepository;
import com.example.mate.repository.PartyRepository;
import com.example.mate.service.PaymentTransactionService;
import com.example.mate.service.TossPaymentService;
import com.example.mate.support.MateTestFixtureFactory;
import com.example.mate.support.MateTestTokenHelper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
        "spring.profiles.active=test",
        "spring.jwt.secret=test-jwt-secret-32-characters-long",
        "spring.jwt.refresh-expiration=86400000",
        "spring.datasource.url=jdbc:h2:mem:mate_flow_abuse;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
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
        "storage.type=oci",
        "oci.s3.endpoint=http://localhost:4566",
        "oci.s3.access-key=test-access-key",
        "oci.s3.secret-key=test-secret-key",
        "oci.s3.bucket=test-bucket",
        "oci.s3.region=ap-seoul-1",
        "spring.autoconfigure.exclude=io.awspring.cloud.autoconfigure.s3.S3AutoConfiguration"
})
@Transactional
class MateFlowPolicyAbuseIntegrationTest {

    private static final String HOST_EMAIL = "mate-host-abuse@example.com";
    private static final String APPLICANT_EMAIL = "mate-applicant-abuse@example.com";
    private static final String OUTSIDER_EMAIL = "mate-outsider-abuse@example.com";

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
    private CheckInRecordRepository checkInRecordRepository;

    @MockitoBean
    private TossPaymentService tossPaymentService;

    @MockitoBean
    private PaymentTransactionService paymentTransactionService;

    @MockitoBean
    private CheerPostRepo cheerPostRepo;

    @MockitoBean
    private CheerReportRepo cheerReportRepo;

    @BeforeEach
    void setUp() {
        checkInRecordRepository.deleteAll();
        partyApplicationRepository.deleteAll();
        partyRepository.deleteAll();
        userProviderRepository.deleteAll();
        userRepository.deleteAll();

        UserEntity host = userRepository.save(MateTestFixtureFactory.user(HOST_EMAIL, "Abuse Host"));
        UserEntity applicant = userRepository.save(MateTestFixtureFactory.user(APPLICANT_EMAIL, "Abuse Applicant"));
        UserEntity outsider = userRepository.save(MateTestFixtureFactory.user(OUTSIDER_EMAIL, "Abuse Outsider"));

        userProviderRepository.save(MateTestFixtureFactory.socialProvider(host, "kakao"));
        userProviderRepository.save(MateTestFixtureFactory.socialProvider(applicant, "naver"));
        userProviderRepository.save(MateTestFixtureFactory.socialProvider(outsider, "kakao"));

        Mockito.doNothing().when(paymentTransactionService).enrichResponse(Mockito.any());
        Mockito.doNothing().when(paymentTransactionService).enrichResponses(Mockito.anyList());
        Mockito.doNothing().when(paymentTransactionService).requestSettlementOnApproval(Mockito.any());
        given(paymentTransactionService.createOrGetOnConfirm(Mockito.any(), Mockito.any(), Mockito.anyString()))
                .willReturn(null);
    }

    @Test
    @DisplayName("SELLING 파티는 결제 없이 FULL 신청 생성을 거부한다")
    void sellingRejectsDirectFullApplicationWithoutPayment() throws Exception {
        UserEntity host = userRepository.findByEmail(HOST_EMAIL).orElseThrow();
        Party sellingParty = partyRepository.save(MateTestFixtureFactory.sellingParty(
                host.getId(), host.getName(), 2, 50000));

        String requestBody = objectMapper.writeValueAsString(Map.of(
                "partyId", sellingParty.getId(),
                "paymentType", "FULL",
                "depositAmount", 50000,
                "message", "직접 FULL 신청"));

        mockMvc.perform(post("/api/applications")
                        .with(MateTestTokenHelper.principalAs(APPLICANT_EMAIL))
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("결제 승인 응답의 amount 위변조(totalAmount 불일치)는 거부한다")
    void confirmRejectsAmountTampering() throws Exception {
        UserEntity host = userRepository.findByEmail(HOST_EMAIL).orElseThrow();
        Party pendingParty = partyRepository.save(MateTestFixtureFactory.pendingParty(
                host.getId(), host.getName(), 2));

        String prepareBody = objectMapper.writeValueAsString(Map.of(
                "partyId", pendingParty.getId(),
                "flowType", "DEPOSIT"));

        String prepareJson = mockMvc.perform(post("/api/payments/toss/prepare")
                        .with(MateTestTokenHelper.principalAs(APPLICANT_EMAIL))
                        .contentType("application/json")
                        .content(prepareBody))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode prepareNode = objectMapper.readTree(prepareJson);
        long intentId = prepareNode.get("intentId").asLong();
        String orderId = prepareNode.get("orderId").asText();
        int expectedAmount = prepareNode.get("amount").asInt();

        given(tossPaymentService.confirmPayment(eq("tampered-pay-key"), eq(orderId), eq(expectedAmount)))
                .willReturn(new TossPaymentDTO.ConfirmResponse(
                        "tampered-pay-key",
                        orderId,
                        "DONE",
                        expectedAmount + 1000,
                        "카드"));

        String confirmBody = objectMapper.writeValueAsString(Map.of(
                "paymentKey", "tampered-pay-key",
                "orderId", orderId,
                "intentId", intentId,
                "partyId", pendingParty.getId(),
                "flowType", PaymentFlowType.DEPOSIT.name(),
                "message", "위변조 시나리오"));

        mockMvc.perform(post("/api/payments/toss/confirm")
                        .with(MateTestTokenHelper.principalAs(APPLICANT_EMAIL))
                        .contentType("application/json")
                        .content(confirmBody))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("동일 orderId 재호출은 기존 신청을 재사용하고 Toss 재승인을 호출하지 않는다")
    void confirmRetryReturnsExistingApplicationWithoutSecondTossConfirm() throws Exception {
        UserEntity host = userRepository.findByEmail(HOST_EMAIL).orElseThrow();
        Party pendingParty = partyRepository.save(MateTestFixtureFactory.pendingParty(
                host.getId(), host.getName(), 2));

        String prepareBody = objectMapper.writeValueAsString(Map.of(
                "partyId", pendingParty.getId(),
                "flowType", "DEPOSIT"));

        String prepareJson = mockMvc.perform(post("/api/payments/toss/prepare")
                        .with(MateTestTokenHelper.principalAs(APPLICANT_EMAIL))
                        .contentType("application/json")
                        .content(prepareBody))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode prepareNode = objectMapper.readTree(prepareJson);
        long intentId = prepareNode.get("intentId").asLong();
        String orderId = prepareNode.get("orderId").asText();
        int expectedAmount = prepareNode.get("amount").asInt();

        given(tossPaymentService.confirmPayment(eq("dup-pay-key"), eq(orderId), eq(expectedAmount)))
                .willReturn(new TossPaymentDTO.ConfirmResponse(
                        "dup-pay-key",
                        orderId,
                        "DONE",
                        expectedAmount,
                        "카드"));

        String confirmBody = objectMapper.writeValueAsString(Map.of(
                "paymentKey", "dup-pay-key",
                "orderId", orderId,
                "intentId", intentId,
                "partyId", pendingParty.getId(),
                "flowType", PaymentFlowType.DEPOSIT.name(),
                "message", "중복 confirm 테스트"));

        mockMvc.perform(post("/api/payments/toss/confirm")
                        .with(MateTestTokenHelper.principalAs(APPLICANT_EMAIL))
                        .contentType("application/json")
                        .content(confirmBody))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/payments/toss/confirm")
                        .with(MateTestTokenHelper.principalAs(APPLICANT_EMAIL))
                        .contentType("application/json")
                        .content(confirmBody))
                .andExpect(status().isOk());

        verify(tossPaymentService, times(1)).confirmPayment(eq("dup-pay-key"), eq(orderId), eq(expectedAmount));
        org.assertj.core.api.Assertions.assertThat(partyApplicationRepository.countByOrderId(orderId)).isEqualTo(1L);
    }

    @Test
    @DisplayName("비참여자는 채팅 조회/전송 및 체크인을 할 수 없다")
    void nonMemberCannotAccessChatOrCheckIn() throws Exception {
        UserEntity host = userRepository.findByEmail(HOST_EMAIL).orElseThrow();
        Party party = partyRepository.save(MateTestFixtureFactory.pendingParty(
                host.getId(), host.getName(), 3));

        String sendBody = objectMapper.writeValueAsString(Map.of(
                "partyId", party.getId(),
                "message", "침입 메시지"));
        mockMvc.perform(post("/api/chat/messages")
                        .with(MateTestTokenHelper.principalAs(OUTSIDER_EMAIL))
                        .contentType("application/json")
                        .content(sendBody))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/chat/party/{partyId}", party.getId())
                        .with(MateTestTokenHelper.principalAs(OUTSIDER_EMAIL)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/chat/party/{partyId}/latest", party.getId())
                        .with(MateTestTokenHelper.principalAs(OUTSIDER_EMAIL)))
                .andExpect(status().isForbidden());

        String checkInBody = objectMapper.writeValueAsString(Map.of(
                "partyId", party.getId(),
                "location", "무단 체크인"));
        mockMvc.perform(post("/api/checkin")
                        .with(MateTestTokenHelper.principalAs(OUTSIDER_EMAIL))
                        .contentType("application/json")
                        .content(checkInBody))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("비호스트는 체크인 QR/수동코드 세션을 발급할 수 없다")
    void nonHostCannotIssueCheckInQrSession() throws Exception {
        UserEntity host = userRepository.findByEmail(HOST_EMAIL).orElseThrow();
        Party party = partyRepository.save(MateTestFixtureFactory.pendingParty(
                host.getId(), host.getName(), 3));

        String requestBody = objectMapper.writeValueAsString(Map.of(
                "partyId", party.getId()));

        mockMvc.perform(post("/api/checkin/qr-session")
                        .with(MateTestTokenHelper.principalAs(APPLICANT_EMAIL))
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("체크인은 qrSessionId/manualCode 중 정확히 하나만 허용한다")
    void checkInRequiresExactlyOneCredential() throws Exception {
        UserEntity host = userRepository.findByEmail(HOST_EMAIL).orElseThrow();
        Party party = partyRepository.save(MateTestFixtureFactory.pendingParty(
                host.getId(), host.getName(), 3));

        String missingCredentialBody = objectMapper.writeValueAsString(Map.of(
                "partyId", party.getId(),
                "location", "잠실야구장"));
        mockMvc.perform(post("/api/checkin")
                        .with(MateTestTokenHelper.principalAs(HOST_EMAIL))
                        .contentType("application/json")
                        .content(missingCredentialBody))
                .andExpect(status().isBadRequest());

        String conflictCredentialBody = objectMapper.writeValueAsString(Map.of(
                "partyId", party.getId(),
                "location", "잠실야구장",
                "qrSessionId", "session-duplicate",
                "manualCode", "1234"));
        mockMvc.perform(post("/api/checkin")
                        .with(MateTestTokenHelper.principalAs(HOST_EMAIL))
                        .contentType("application/json")
                        .content(conflictCredentialBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("중복 신청, 거절 후 재신청, orderId/paymentKey 재사용을 거부한다")
    void rejectsDuplicateReapplyAndArtifactReuse() throws Exception {
        UserEntity host = userRepository.findByEmail(HOST_EMAIL).orElseThrow();
        UserEntity applicant = userRepository.findByEmail(APPLICANT_EMAIL).orElseThrow();
        UserEntity outsider = userRepository.findByEmail(OUTSIDER_EMAIL).orElseThrow();

        Party firstParty = partyRepository.save(MateTestFixtureFactory.pendingParty(
                host.getId(), host.getName(), 3));

        String firstApplyBody = objectMapper.writeValueAsString(Map.of(
                "partyId", firstParty.getId(),
                "paymentType", "DEPOSIT",
                "depositAmount", 22000,
                "message", "첫 신청"));

        String firstApplyJson = mockMvc.perform(post("/api/applications")
                        .with(MateTestTokenHelper.principalAs(APPLICANT_EMAIL))
                        .contentType("application/json")
                        .content(firstApplyBody))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode firstApplyNode = objectMapper.readTree(firstApplyJson);
        long firstApplicationId = firstApplyNode.get("id").asLong();

        mockMvc.perform(post("/api/applications")
                        .with(MateTestTokenHelper.principalAs(APPLICANT_EMAIL))
                        .contentType("application/json")
                        .content(firstApplyBody))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/applications/{id}/reject", firstApplicationId)
                        .with(MateTestTokenHelper.principalAs(HOST_EMAIL)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/applications")
                        .with(MateTestTokenHelper.principalAs(APPLICANT_EMAIL))
                        .contentType("application/json")
                        .content(firstApplyBody))
                .andExpect(status().isBadRequest());

        Party paidParty = partyRepository.save(MateTestFixtureFactory.pendingParty(
                host.getId(), host.getName(), 3));
        PartyApplication existingPaidApplication = MateTestFixtureFactory.application(
                paidParty.getId(),
                applicant.getId(),
                applicant.getName(),
                PartyApplication.PaymentType.DEPOSIT,
                true,
                false,
                false,
                "MATE-DUP-ORDER",
                "MATE-DUP-KEY");
        partyApplicationRepository.save(existingPaidApplication);

        Party anotherParty = partyRepository.save(MateTestFixtureFactory.pendingParty(
                host.getId(), host.getName(), 3));

        given(tossPaymentService.confirmPayment(eq("MATE-DUP-KEY"), eq("MATE-DUP-ORDER"), anyInt()))
                .willAnswer(invocation -> new TossPaymentDTO.ConfirmResponse(
                        "MATE-DUP-KEY",
                        "MATE-DUP-ORDER",
                        "DONE",
                        invocation.getArgument(2),
                        "카드"));
        given(tossPaymentService.cancelPayment(eq("MATE-DUP-KEY"), Mockito.anyString(), Mockito.anyInt()))
                .willReturn(new TossPaymentDTO.CancelResponse("MATE-DUP-KEY", "CANCELED", 0));

        String reuseConfirmBody = objectMapper.writeValueAsString(Map.of(
                "paymentKey", "MATE-DUP-KEY",
                "orderId", "MATE-DUP-ORDER",
                "partyId", anotherParty.getId(),
                "flowType", PaymentFlowType.DEPOSIT.name(),
                "message", "재사용 시도"));

        mockMvc.perform(post("/api/payments/toss/confirm")
                        .with(MateTestTokenHelper.principalAs(OUTSIDER_EMAIL))
                        .contentType("application/json")
                        .content(reuseConfirmBody))
                .andExpect(status().isBadRequest());
    }
}
