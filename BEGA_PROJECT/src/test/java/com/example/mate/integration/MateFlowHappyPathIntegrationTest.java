package com.example.mate.integration;

import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserProviderRepository;
import com.example.auth.repository.UserRepository;
import com.example.cheerboard.repo.CheerPostRepo;
import com.example.cheerboard.repo.CheerReportRepo;
import com.example.mate.dto.TossPaymentDTO;
import com.example.mate.entity.Party;
import com.example.mate.entity.PaymentFlowType;
import com.example.mate.repository.CheckInRecordRepository;
import com.example.mate.repository.PartyApplicationRepository;
import com.example.mate.repository.PartyRepository;
import com.example.mate.scheduler.PartyLifecycleScheduler;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
        "spring.profiles.active=test",
        "spring.jwt.secret=test-jwt-secret-32-characters-long",
        "spring.jwt.refresh-expiration=86400000",
        "spring.datasource.url=jdbc:h2:mem:mate_flow_happy;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
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
class MateFlowHappyPathIntegrationTest {

    private static final String HOST_EMAIL = "mate-host-happy@example.com";
    private static final String APPLICANT_EMAIL = "mate-applicant-happy@example.com";

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

    @Autowired
    private PartyLifecycleScheduler partyLifecycleScheduler;

    @MockitoBean
    private TossPaymentService tossPaymentService;

    @MockitoBean
    private CheerPostRepo cheerPostRepo;

    @MockitoBean
    private CheerReportRepo cheerReportRepo;

    @MockitoBean
    private PaymentTransactionService paymentTransactionService;

    @MockitoBean
    private StringRedisTemplate redisTemplate;

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOperations = Mockito.mock(ValueOperations.class);

    private final ConcurrentMap<String, String> redisStore = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() {
        checkInRecordRepository.deleteAll();
        partyApplicationRepository.deleteAll();
        partyRepository.deleteAll();
        userProviderRepository.deleteAll();
        userRepository.deleteAll();

        UserEntity host = userRepository.save(MateTestFixtureFactory.user(HOST_EMAIL, "Happy Host"));
        userProviderRepository.save(MateTestFixtureFactory.socialProvider(host, "kakao"));

        UserEntity applicant = userRepository.save(MateTestFixtureFactory.user(APPLICANT_EMAIL, "Happy Applicant"));
        userProviderRepository.save(MateTestFixtureFactory.socialProvider(applicant, "naver"));

        Mockito.doNothing().when(paymentTransactionService).enrichResponse(Mockito.any());
        Mockito.doNothing().when(paymentTransactionService).enrichResponses(Mockito.anyList());
        Mockito.doNothing().when(paymentTransactionService).requestSettlementOnApproval(Mockito.any());
        given(paymentTransactionService.createOrGetOnConfirm(Mockito.any(), Mockito.any(), Mockito.anyString()))
                .willReturn(null);

        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        Mockito.doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            String value = invocation.getArgument(1);
            redisStore.put(key, value);
            return null;
        }).when(valueOperations).set(Mockito.anyString(), Mockito.anyString(), Mockito.any(Duration.class));
        given(valueOperations.get(Mockito.anyString()))
                .willAnswer(invocation -> redisStore.get(invocation.getArgument(0)));
    }

    @Test
    @DisplayName("Happy path: 파티 생성 -> 결제 승인 신청 -> 호스트 승인 -> 채팅 -> 체크인 -> 자동 COMPLETED")
    void happyPath_endToEndFlow() throws Exception {
        String createPartyBody = objectMapper.writeValueAsString(Map.ofEntries(
                Map.entry("hostName", "Happy Host"),
                Map.entry("teamId", "LG"),
                Map.entry("gameDate", LocalDate.now().plusDays(1).toString()),
                Map.entry("gameTime", "18:30:00"),
                Map.entry("stadium", "잠실"),
                Map.entry("homeTeam", "LG"),
                Map.entry("awayTeam", "OB"),
                Map.entry("section", "1루"),
                Map.entry("maxParticipants", 2),
                Map.entry("description", "happy path party"),
                Map.entry("ticketPrice", 12000)));

        String createdPartyJson = mockMvc.perform(post("/api/parties")
                        .with(MateTestTokenHelper.principalAs(HOST_EMAIL))
                        .contentType("application/json")
                        .content(createPartyBody))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode partyNode = objectMapper.readTree(createdPartyJson);
        long partyId = partyNode.get("id").asLong();
        assertThat(partyNode.get("status").asText()).isEqualTo("PENDING");

        String prepareBody = objectMapper.writeValueAsString(Map.of(
                "partyId", partyId,
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
        int amount = prepareNode.get("amount").asInt();
        assertThat(amount).isGreaterThan(0);

        given(tossPaymentService.confirmPayment(eq("pay-key-happy"), eq(orderId), eq(amount)))
                .willReturn(new TossPaymentDTO.ConfirmResponse(
                        "pay-key-happy",
                        orderId,
                        "DONE",
                        amount,
                        "카드"));

        String confirmBody = objectMapper.writeValueAsString(Map.of(
                "paymentKey", "pay-key-happy",
                "orderId", orderId,
                "intentId", intentId,
                "partyId", partyId,
                "flowType", PaymentFlowType.DEPOSIT.name(),
                "message", "승인 부탁드려요"));

        String confirmJson = mockMvc.perform(post("/api/payments/toss/confirm")
                        .with(MateTestTokenHelper.principalAs(APPLICANT_EMAIL))
                        .contentType("application/json")
                        .content(confirmBody))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode applicationNode = objectMapper.readTree(confirmJson);
        long applicationId = applicationNode.get("id").asLong();
        assertThat(applicationNode.get("isPaid").asBoolean()).isTrue();
        assertThat(applicationNode.get("isApproved").asBoolean()).isFalse();

        mockMvc.perform(post("/api/applications/{id}/approve", applicationId)
                        .with(MateTestTokenHelper.principalAs(HOST_EMAIL)))
                .andExpect(status().isOk());

        String qrSessionBody = objectMapper.writeValueAsString(Map.of("partyId", partyId));
        String qrSessionJson = mockMvc.perform(post("/api/checkin/qr-session")
                        .with(MateTestTokenHelper.principalAs(HOST_EMAIL))
                        .contentType("application/json")
                        .content(qrSessionBody))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode qrSessionNode = objectMapper.readTree(qrSessionJson);
        String manualCode = qrSessionNode.get("manualCode").asText();

        Party matchedParty = partyRepository.findById(partyId).orElseThrow();
        assertThat(matchedParty.getCurrentParticipants()).isEqualTo(2);
        assertThat(matchedParty.getStatus()).isEqualTo(Party.PartyStatus.MATCHED);

        String hostChatBody = objectMapper.writeValueAsString(Map.of(
                "partyId", partyId,
                "message", "환영합니다"));
        mockMvc.perform(post("/api/chat/messages")
                        .with(MateTestTokenHelper.principalAs(HOST_EMAIL))
                        .contentType("application/json")
                        .content(hostChatBody))
                .andExpect(status().isCreated());

        String applicantChatBody = objectMapper.writeValueAsString(Map.of(
                "partyId", partyId,
                "message", "잘 부탁드립니다"));
        mockMvc.perform(post("/api/chat/messages")
                        .with(MateTestTokenHelper.principalAs(APPLICANT_EMAIL))
                        .contentType("application/json")
                        .content(applicantChatBody))
                .andExpect(status().isCreated());

        String hostCheckInBody = objectMapper.writeValueAsString(Map.of(
                "partyId", partyId,
                "location", "잠실 1루 게이트",
                "manualCode", manualCode));
        mockMvc.perform(post("/api/checkin")
                        .with(MateTestTokenHelper.principalAs(HOST_EMAIL))
                        .contentType("application/json")
                        .content(hostCheckInBody))
                .andExpect(status().isCreated());

        String applicantCheckInBody = objectMapper.writeValueAsString(Map.of(
                "partyId", partyId,
                "location", "잠실 1루 게이트",
                "manualCode", manualCode));
        mockMvc.perform(post("/api/checkin")
                        .with(MateTestTokenHelper.principalAs(APPLICANT_EMAIL))
                        .contentType("application/json")
                        .content(applicantCheckInBody))
                .andExpect(status().isCreated());

        Party checkedInParty = partyRepository.findById(partyId).orElseThrow();
        assertThat(checkedInParty.getStatus()).isEqualTo(Party.PartyStatus.CHECKED_IN);

        checkedInParty.setGameDate(LocalDate.now().minusDays(1));
        checkedInParty.setGameTime(LocalTime.of(13, 0));
        partyRepository.save(checkedInParty);

        partyLifecycleScheduler.managePartyLifecycle();

        Party completedParty = partyRepository.findById(partyId).orElseThrow();
        assertThat(completedParty.getStatus()).isEqualTo(Party.PartyStatus.COMPLETED);
    }

    @Test
    @DisplayName("Happy path: 판매 전환(PENDING->SELLING) 후 SELLING_FULL 결제 승인 성공")
    void sellingFlow_convertAndConfirmPayment() throws Exception {
        String createPartyBody = objectMapper.writeValueAsString(Map.ofEntries(
                Map.entry("hostName", "Happy Host"),
                Map.entry("teamId", "LG"),
                Map.entry("gameDate", LocalDate.now().plusDays(1).toString()),
                Map.entry("gameTime", "18:30:00"),
                Map.entry("stadium", "잠실"),
                Map.entry("homeTeam", "LG"),
                Map.entry("awayTeam", "OB"),
                Map.entry("section", "1루"),
                Map.entry("maxParticipants", 2),
                Map.entry("description", "selling happy path"),
                Map.entry("ticketPrice", 12000)));

        String createdPartyJson = mockMvc.perform(post("/api/parties")
                        .with(MateTestTokenHelper.principalAs(HOST_EMAIL))
                        .contentType("application/json")
                        .content(createPartyBody))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode partyNode = objectMapper.readTree(createdPartyJson);
        long partyId = partyNode.get("id").asLong();

        String sellingPatchBody = objectMapper.writeValueAsString(Map.of(
                "status", Party.PartyStatus.SELLING.name(),
                "price", 50000));

        String sellingPartyJson = mockMvc.perform(patch("/api/parties/{id}", partyId)
                        .with(MateTestTokenHelper.principalAs(HOST_EMAIL))
                        .contentType("application/json")
                        .content(sellingPatchBody))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode sellingPartyNode = objectMapper.readTree(sellingPartyJson);
        assertThat(sellingPartyNode.get("status").asText()).isEqualTo(Party.PartyStatus.SELLING.name());
        assertThat(sellingPartyNode.get("price").asInt()).isEqualTo(50000);

        String prepareBody = objectMapper.writeValueAsString(Map.of(
                "partyId", partyId,
                "flowType", PaymentFlowType.SELLING_FULL.name()));

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
        int amount = prepareNode.get("amount").asInt();
        assertThat(amount).isEqualTo(50000);

        given(tossPaymentService.confirmPayment(eq("pay-key-selling-happy"), eq(orderId), eq(amount)))
                .willReturn(new TossPaymentDTO.ConfirmResponse(
                        "pay-key-selling-happy",
                        orderId,
                        "DONE",
                        amount,
                        "카드"));

        String confirmBody = objectMapper.writeValueAsString(Map.of(
                "paymentKey", "pay-key-selling-happy",
                "orderId", orderId,
                "intentId", intentId,
                "partyId", partyId,
                "flowType", PaymentFlowType.SELLING_FULL.name(),
                "paymentType", "FULL",
                "message", "티켓 구매 요청"));

        String firstConfirmJson = mockMvc.perform(post("/api/payments/toss/confirm")
                        .with(MateTestTokenHelper.principalAs(APPLICANT_EMAIL))
                        .contentType("application/json")
                        .content(confirmBody))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode firstConfirmNode = objectMapper.readTree(firstConfirmJson);
        long applicationId = firstConfirmNode.get("id").asLong();
        assertThat(firstConfirmNode.get("paymentType").asText()).isEqualTo("FULL");
        assertThat(firstConfirmNode.get("isPaid").asBoolean()).isTrue();
        assertThat(firstConfirmNode.get("isApproved").asBoolean()).isTrue();

        String secondConfirmJson = mockMvc.perform(post("/api/payments/toss/confirm")
                        .with(MateTestTokenHelper.principalAs(APPLICANT_EMAIL))
                        .contentType("application/json")
                        .content(confirmBody))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode secondConfirmNode = objectMapper.readTree(secondConfirmJson);
        assertThat(secondConfirmNode.get("id").asLong()).isEqualTo(applicationId);

        Party matchedParty = partyRepository.findById(partyId).orElseThrow();
        assertThat(matchedParty.getCurrentParticipants()).isEqualTo(2);
        assertThat(matchedParty.getStatus()).isEqualTo(Party.PartyStatus.MATCHED);

        verify(tossPaymentService, times(1)).confirmPayment(eq("pay-key-selling-happy"), eq(orderId), eq(amount));
    }
}
