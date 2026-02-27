package com.example.mate.integration;

import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserProviderRepository;
import com.example.auth.repository.UserRepository;
import com.example.cheerboard.repo.CheerPostRepo;
import com.example.cheerboard.repo.CheerReportRepo;
import com.example.mate.entity.Party;
import com.example.mate.repository.CheckInRecordRepository;
import com.example.mate.repository.PartyApplicationRepository;
import com.example.mate.repository.PartyRepository;
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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
        "spring.profiles.active=test",
        "spring.jwt.secret=test-jwt-secret-32-characters-long",
        "spring.jwt.refresh-expiration=86400000",
        "spring.datasource.url=jdbc:h2:mem:mate_direct_trade_mode;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
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
        "mate.payment.mode=DIRECT_TRADE",
        "payment.selling.enforced=true",
        "storage.type=oci",
        "oci.s3.endpoint=http://localhost:4566",
        "oci.s3.access-key=test-access-key",
        "oci.s3.secret-key=test-secret-key",
        "oci.s3.bucket=test-bucket",
        "oci.s3.region=ap-seoul-1",
        "spring.autoconfigure.exclude=io.awspring.cloud.autoconfigure.s3.S3AutoConfiguration"
})
class MateDirectTradeModeIntegrationTest {

    private static final String HOST_EMAIL = "mate-host-direct@example.com";
    private static final String APPLICANT_EMAIL = "mate-applicant-direct@example.com";

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

        UserEntity host = userRepository.save(MateTestFixtureFactory.user(HOST_EMAIL, "Direct Host"));
        UserEntity applicant = userRepository.save(MateTestFixtureFactory.user(APPLICANT_EMAIL, "Direct Applicant"));
        userProviderRepository.save(MateTestFixtureFactory.socialProvider(host, "kakao"));
        userProviderRepository.save(MateTestFixtureFactory.socialProvider(applicant, "naver"));
    }

    @Test
    @DisplayName("DIRECT_TRADE 모드에서는 Toss prepare/confirm API가 503을 반환한다")
    void tossEndpointsReturnServiceUnavailableInDirectTradeMode() throws Exception {
        Party party = partyRepository.save(MateTestFixtureFactory.pendingParty(
                userRepository.findByEmail(HOST_EMAIL).orElseThrow().getId(),
                "Direct Host",
                2));

        String prepareBody = objectMapper.writeValueAsString(Map.of(
                "partyId", party.getId(),
                "flowType", "DEPOSIT"));
        mockMvc.perform(post("/api/payments/toss/prepare")
                        .with(MateTestTokenHelper.principalAs(APPLICANT_EMAIL))
                        .contentType("application/json")
                        .content(prepareBody))
                .andExpect(status().isServiceUnavailable());

        String confirmBody = objectMapper.writeValueAsString(Map.of(
                "paymentKey", "direct-trade-pk",
                "orderId", "MATE-1-1-1735123456789",
                "partyId", party.getId(),
                "flowType", "DEPOSIT",
                "message", "직거래 모드 확인"));
        mockMvc.perform(post("/api/payments/toss/confirm")
                        .with(MateTestTokenHelper.principalAs(APPLICANT_EMAIL))
                        .contentType("application/json")
                        .content(confirmBody))
                .andExpect(status().isServiceUnavailable());

        verifyNoInteractions(tossPaymentService);
    }

    @Test
    @DisplayName("DIRECT_TRADE 모드에서 SELLING FULL 직접 신청은 생성되지만 자동 승인되지 않는다")
    void directTradeSellingFullApplicationIsCreatedWithoutAutoApproval() throws Exception {
        UserEntity host = userRepository.findByEmail(HOST_EMAIL).orElseThrow();
        Party sellingParty = partyRepository.save(MateTestFixtureFactory.sellingParty(
                host.getId(),
                host.getName(),
                2,
                50000));

        String requestBody = objectMapper.writeValueAsString(Map.of(
                "partyId", sellingParty.getId(),
                "paymentType", "FULL",
                "depositAmount", 50000,
                "message", "직거래 판매 신청"));

        String createdJson = mockMvc.perform(post("/api/applications")
                        .with(MateTestTokenHelper.principalAs(APPLICANT_EMAIL))
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode createdNode = objectMapper.readTree(createdJson);
        assertThat(createdNode.get("paymentType").asText()).isEqualTo("FULL");
        assertThat(createdNode.get("isPaid").asBoolean()).isFalse();
        assertThat(createdNode.get("isApproved").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("DIRECT_TRADE 무결제 신청 취소는 NO_PAYMENT를 반환하고 Toss 취소를 호출하지 않는다")
    void directTradeCancelReturnsNoPaymentWithoutTossCancel() throws Exception {
        UserEntity host = userRepository.findByEmail(HOST_EMAIL).orElseThrow();
        Party party = partyRepository.save(MateTestFixtureFactory.pendingParty(
                host.getId(),
                host.getName(),
                3));

        String createBody = objectMapper.writeValueAsString(Map.of(
                "partyId", party.getId(),
                "paymentType", "DEPOSIT",
                "depositAmount", 10000,
                "message", "직거래 신청"));
        String createdJson = mockMvc.perform(post("/api/applications")
                        .with(MateTestTokenHelper.principalAs(APPLICANT_EMAIL))
                        .contentType("application/json")
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long applicationId = objectMapper.readTree(createdJson).get("id").asLong();

        String cancelBody = objectMapper.writeValueAsString(Map.of(
                "cancelReasonType", "OTHER",
                "cancelMemo", "직거래 취소"));
        String cancelJson = mockMvc.perform(post("/api/applications/{applicationId}/cancel", applicationId)
                        .with(MateTestTokenHelper.principalAs(APPLICANT_EMAIL))
                        .contentType("application/json")
                        .content(cancelBody))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode cancelNode = objectMapper.readTree(cancelJson);
        assertThat(cancelNode.get("refundPolicyApplied").asText()).isEqualTo("NO_PAYMENT");
        assertThat(cancelNode.get("refundAmount").asInt()).isEqualTo(0);
        assertThat(cancelNode.get("feeCharged").asInt()).isEqualTo(0);
        verify(tossPaymentService, never()).cancelPayment(anyString(), anyString(), anyInt());
    }
}
