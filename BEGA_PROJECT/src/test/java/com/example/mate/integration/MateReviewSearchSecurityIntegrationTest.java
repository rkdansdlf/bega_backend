package com.example.mate.integration;

import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserRepository;
import com.example.auth.util.JWTUtil;
import com.example.cheerboard.repo.CheerPostRepo;
import com.example.cheerboard.repo.CheerReportRepo;
import com.example.common.ratelimit.RateLimitService;
import com.example.mate.entity.Party;
import com.example.mate.entity.PartyApplication;
import com.example.mate.entity.PartyReview;
import com.example.mate.repository.MateSearchTermRepository;
import com.example.mate.repository.PartyApplicationRepository;
import com.example.mate.repository.PartyRepository;
import com.example.mate.repository.PartyReviewRepository;
import com.example.mate.service.PaymentTransactionService;
import com.example.mate.service.TossPaymentService;
import com.example.mate.support.MateTestFixtureFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.profiles.active=test",
        "spring.jwt.secret=test-jwt-secret-64-characters-long-for-hs512-signature-tests-key-1234567890",
        "spring.jwt.refresh-expiration=86400000",
        "spring.datasource.url=jdbc:h2:mem:mate_review_search_security;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.jpa.open-in-view=false",
        "jobrunr.background-job-server.enabled=false",
        "jobrunr.dashboard.enabled=false",
        "storage.type=oci",
        "oci.s3.endpoint=http://localhost:4566",
        "oci.s3.access-key=test-access-key",
        "oci.s3.secret-key=test-secret-key",
        "oci.s3.bucket=test-bucket",
        "oci.s3.region=ap-seoul-1",
        "app.allowed-origins=http://localhost:5176",
        "app.frontend.url=http://localhost:5176"
})
@Transactional
@DisplayName("Mate review/search security integration tests")
class MateReviewSearchSecurityIntegrationTest {

    private static final String FRONTEND_ORIGIN = "http://localhost:5176";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JWTUtil jwtUtil;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PartyRepository partyRepository;

    @Autowired
    private PartyApplicationRepository partyApplicationRepository;

    @Autowired
    private PartyReviewRepository partyReviewRepository;

    @Autowired
    private MateSearchTermRepository mateSearchTermRepository;

    @MockitoBean
    private TossPaymentService tossPaymentService;

    @MockitoBean
    private PaymentTransactionService paymentTransactionService;

    @MockitoBean
    private RateLimitService rateLimitService;

    @MockitoBean
    private CheerPostRepo cheerPostRepo;

    @MockitoBean
    private CheerReportRepo cheerReportRepo;

    private UserEntity host;
    private UserEntity approved;
    private UserEntity pending;
    private UserEntity outsider;
    private Party party;

    @BeforeEach
    void setUp() {
        mateSearchTermRepository.deleteAll();
        partyReviewRepository.deleteAll();
        partyApplicationRepository.deleteAll();
        partyRepository.deleteAll();
        userRepository.deleteAll();

        Mockito.when(rateLimitService.isAllowed(
                Mockito.anyString(),
                Mockito.anyInt(),
                Mockito.anyInt(),
                Mockito.anyBoolean())).thenReturn(true);

        host = userRepository.save(MateTestFixtureFactory.user("mate-review-host@example.com", "Review Host"));
        approved = userRepository.save(MateTestFixtureFactory.user("mate-review-approved@example.com", "Review Approved"));
        pending = userRepository.save(MateTestFixtureFactory.user("mate-review-pending@example.com", "Review Pending"));
        outsider = userRepository.save(MateTestFixtureFactory.user("mate-review-outsider@example.com", "Review Outsider"));

        party = partyRepository.save(MateTestFixtureFactory.party(
                host.getId(),
                host.getName(),
                Party.PartyStatus.COMPLETED,
                4,
                2,
                12000,
                null,
                LocalDate.now().minusDays(1),
                LocalTime.of(18, 30)));

        partyApplicationRepository.save(MateTestFixtureFactory.application(
                party.getId(),
                approved.getId(),
                approved.getName(),
                PartyApplication.PaymentType.DEPOSIT,
                true,
                true,
                false,
                "REVIEW-APPROVED-ORDER",
                "REVIEW-APPROVED-KEY"));
        partyApplicationRepository.save(MateTestFixtureFactory.application(
                party.getId(),
                pending.getId(),
                pending.getName(),
                PartyApplication.PaymentType.DEPOSIT,
                false,
                false,
                false,
                null,
                null));
        partyReviewRepository.save(PartyReview.builder()
                .partyId(party.getId())
                .reviewerId(host.getId())
                .revieweeId(approved.getId())
                .rating(5)
                .comment("좋았어요")
                .build());

        Mockito.doNothing().when(paymentTransactionService).enrichResponse(Mockito.any());
        Mockito.doNothing().when(paymentTransactionService).enrichResponses(Mockito.anyList());
    }

    @Test
    @DisplayName("party review list requires host or approved participant")
    void partyReviewListRequiresHostOrApprovedParticipant() throws Exception {
        mockMvc.perform(get("/api/reviews/party/{partyId}", party.getId()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/reviews/party/{partyId}", party.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(host)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/reviews/party/{partyId}", party.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(approved)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/reviews/party/{partyId}", party.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(pending)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/reviews/party/{partyId}", party.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(outsider)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("search-term record requires authentication while popular search terms stay public")
    void searchTermRecordRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/parties/search-terms/popular"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/parties/search-terms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"term\":\"잠실 블루존\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/parties/search-terms")
                        .header(HttpHeaders.AUTHORIZATION, bearer(host))
                        .header(HttpHeaders.ORIGIN, FRONTEND_ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"term\":\"  잠실   블루존  \"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/parties/search-terms/popular")
                        .param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].term").value("잠실 블루존"))
                .andExpect(jsonPath("$[0].count").value(1))
                .andExpect(jsonPath("$[0].rank").value(1));
    }

    private String bearer(UserEntity user) {
        return "Bearer " + jwtUtil.createJwt(
                user.getEmail(),
                user.getRole(),
                user.getId(),
                60_000L,
                user.getTokenVersion());
    }
}
