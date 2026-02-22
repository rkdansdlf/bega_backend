package com.example.mate.integration;

import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserProviderRepository;
import com.example.auth.repository.UserRepository;
import com.example.mate.entity.Party;
import com.example.mate.entity.PartyApplication;
import com.example.mate.repository.CheckInRecordRepository;
import com.example.mate.repository.PartyApplicationRepository;
import com.example.mate.repository.PartyRepository;
import com.example.mate.service.PaymentTransactionService;
import com.example.mate.service.TossPaymentService;
import com.example.mate.support.MateTestFixtureFactory;
import com.example.mate.support.MateTestTokenHelper;
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

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
        "spring.profiles.active=test",
        "spring.jwt.secret=test-jwt-secret-32-characters-long",
        "spring.jwt.refresh-expiration=86400000",
        "spring.datasource.url=jdbc:h2:mem:mate_flow_auth;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
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
        "storage.type=oci",
        "oci.s3.endpoint=http://localhost:4566",
        "oci.s3.access-key=test-access-key",
        "oci.s3.secret-key=test-secret-key",
        "oci.s3.bucket=test-bucket",
        "oci.s3.region=ap-seoul-1",
        "spring.autoconfigure.exclude=io.awspring.cloud.autoconfigure.s3.S3AutoConfiguration"
})
@Transactional
class MateFlowAuthBoundaryIntegrationTest {

    private static final String HOST_EMAIL = "mate-host-auth@example.com";
    private static final String APPROVED_EMAIL = "mate-approved-auth@example.com";
    private static final String PENDING_EMAIL = "mate-pending-auth@example.com";
    private static final String OUTSIDER_EMAIL = "mate-outsider-auth@example.com";

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

    @BeforeEach
    void setUp() {
        checkInRecordRepository.deleteAll();
        partyApplicationRepository.deleteAll();
        partyRepository.deleteAll();
        userProviderRepository.deleteAll();
        userRepository.deleteAll();

        UserEntity host = userRepository.save(MateTestFixtureFactory.user(HOST_EMAIL, "Auth Host"));
        UserEntity approved = userRepository.save(MateTestFixtureFactory.user(APPROVED_EMAIL, "Auth Approved"));
        UserEntity pending = userRepository.save(MateTestFixtureFactory.user(PENDING_EMAIL, "Auth Pending"));
        UserEntity outsider = userRepository.save(MateTestFixtureFactory.user(OUTSIDER_EMAIL, "Auth Outsider"));

        userProviderRepository.save(MateTestFixtureFactory.socialProvider(host, "kakao"));
        userProviderRepository.save(MateTestFixtureFactory.socialProvider(approved, "naver"));
        userProviderRepository.save(MateTestFixtureFactory.socialProvider(pending, "kakao"));
        userProviderRepository.save(MateTestFixtureFactory.socialProvider(outsider, "naver"));

        Party party = MateTestFixtureFactory.pendingParty(host.getId(), host.getName(), 4);
        party.setCurrentParticipants(2);
        Party savedParty = partyRepository.save(party);

        PartyApplication approvedApplication = MateTestFixtureFactory.application(
                savedParty.getId(),
                approved.getId(),
                approved.getName(),
                PartyApplication.PaymentType.DEPOSIT,
                true,
                true,
                false,
                "AUTH-APPROVED-ORDER",
                "AUTH-APPROVED-KEY");
        partyApplicationRepository.save(approvedApplication);

        PartyApplication pendingApplication = MateTestFixtureFactory.application(
                savedParty.getId(),
                pending.getId(),
                pending.getName(),
                PartyApplication.PaymentType.DEPOSIT,
                false,
                false,
                false,
                null,
                null);
        partyApplicationRepository.save(pendingApplication);

        Mockito.doNothing().when(paymentTransactionService).enrichResponse(Mockito.any());
        Mockito.doNothing().when(paymentTransactionService).enrichResponses(Mockito.anyList());
        Mockito.doNothing().when(paymentTransactionService).requestSettlementOnApproval(Mockito.any());
        given(paymentTransactionService.createOrGetOnConfirm(Mockito.any(), Mockito.any(), Mockito.anyString()))
                .willReturn(null);
    }

    @Test
    @DisplayName("채팅 권한 경계: 호스트/승인 참여자 허용, 대기/비참여자 차단")
    void chatAccessBoundaryByMembership() throws Exception {
        Party party = partyRepository.findAll().stream().findFirst().orElseThrow();

        String hostMessageBody = objectMapper.writeValueAsString(Map.of(
                "partyId", party.getId(),
                "message", "권한 경계 테스트 시작"));
        mockMvc.perform(post("/api/chat/messages")
                        .with(MateTestTokenHelper.principalAs(HOST_EMAIL))
                        .contentType("application/json")
                        .content(hostMessageBody))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/chat/party/{partyId}", party.getId())
                        .with(MateTestTokenHelper.principalAs(HOST_EMAIL)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/chat/party/{partyId}/latest", party.getId())
                        .with(MateTestTokenHelper.principalAs(HOST_EMAIL)))
                .andExpect(status().isOk());

        String approvedMessageBody = objectMapper.writeValueAsString(Map.of(
                "partyId", party.getId(),
                "message", "승인 참여자 메시지"));
        mockMvc.perform(post("/api/chat/messages")
                        .with(MateTestTokenHelper.principalAs(APPROVED_EMAIL))
                        .contentType("application/json")
                        .content(approvedMessageBody))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/chat/party/{partyId}", party.getId())
                        .with(MateTestTokenHelper.principalAs(APPROVED_EMAIL)))
                .andExpect(status().isOk());

        String pendingMessageBody = objectMapper.writeValueAsString(Map.of(
                "partyId", party.getId(),
                "message", "대기 참여자 메시지"));
        mockMvc.perform(post("/api/chat/messages")
                        .with(MateTestTokenHelper.principalAs(PENDING_EMAIL))
                        .contentType("application/json")
                        .content(pendingMessageBody))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/chat/party/{partyId}", party.getId())
                        .with(MateTestTokenHelper.principalAs(PENDING_EMAIL)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/chat/party/{partyId}/latest", party.getId())
                        .with(MateTestTokenHelper.principalAs(PENDING_EMAIL)))
                .andExpect(status().isForbidden());

        String outsiderMessageBody = objectMapper.writeValueAsString(Map.of(
                "partyId", party.getId(),
                "message", "외부인 메시지"));
        mockMvc.perform(post("/api/chat/messages")
                        .with(MateTestTokenHelper.principalAs(OUTSIDER_EMAIL))
                        .contentType("application/json")
                        .content(outsiderMessageBody))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/chat/party/{partyId}", party.getId())
                        .with(MateTestTokenHelper.principalAs(OUTSIDER_EMAIL)))
                .andExpect(status().isForbidden());
    }
}
