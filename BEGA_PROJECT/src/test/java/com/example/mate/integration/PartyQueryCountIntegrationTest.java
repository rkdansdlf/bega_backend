package com.example.mate.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.auth.service.PublicVisibilityVerifier;
import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserRepository;
import com.example.kbo.service.TicketVerificationTokenStore;
import com.example.mate.dto.PartyDTO;
import com.example.mate.entity.Party;
import com.example.mate.entity.PartyReview;
import com.example.mate.repository.PartyRepository;
import com.example.mate.repository.PartyReviewRepository;
import com.example.mate.service.PartyService;
import com.example.mate.support.MateTestFixtureFactory;
import com.example.support.HibernateQueryCountSupport;
import com.example.support.HibernateStatisticsTestConfig;
import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDate;
import java.time.LocalTime;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import com.example.notification.service.NotificationService;
import com.example.profile.storage.service.ProfileImageService;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@DataJpaTest
@Import({
        PartyService.class,
        PublicVisibilityVerifier.class,
        HibernateStatisticsTestConfig.class
})
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:party_query_count;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "spring.jpa.properties.hibernate.show_sql=false",
        "logging.level.org.hibernate.SQL=ERROR",
        "logging.level.org.hibernate.orm.jdbc.bind=ERROR"
})
class PartyQueryCountIntegrationTest {

    @Autowired
    private PartyService partyService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PartyRepository partyRepository;

    @Autowired
    private PartyReviewRepository partyReviewRepository;

    @Autowired
    @Qualifier("entityManagerFactory")
    private EntityManagerFactory entityManagerFactory;

    @MockitoBean
    private NotificationService notificationService;

    @MockitoBean
    private ProfileImageService profileImageService;

    @MockitoBean
    private TicketVerificationTokenStore ticketVerificationTokenStore;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new com.example.mate.controller.PartyController(partyService)).build();
        partyReviewRepository.deleteAll();
        partyRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("public party list keeps query count bounded across multiple parties")
    void getAllParties_keepsPrepareStatementCountBoundedAcrossMultipleParties() {
        UserEntity firstHost = userRepository.save(MateTestFixtureFactory.user("host-one@example.com", "Host One"));
        UserEntity secondHost = userRepository.save(MateTestFixtureFactory.user("host-two@example.com", "Host Two"));

        Party firstParty = partyRepository.save(buildParty(firstHost, "첫 번째 파티", LocalDate.of(2026, 4, 1), LocalTime.of(18, 30)));
        partyRepository.save(buildParty(firstHost, "두 번째 파티", LocalDate.of(2026, 4, 2), LocalTime.of(18, 30)));
        Party thirdParty = partyRepository.save(buildParty(secondHost, "세 번째 파티", LocalDate.of(2026, 4, 3), LocalTime.of(18, 30)));

        partyReviewRepository.save(PartyReview.builder()
                .partyId(firstParty.getId())
                .reviewerId(secondHost.getId())
                .revieweeId(firstHost.getId())
                .rating(5)
                .comment("great host")
                .build());
        partyReviewRepository.save(PartyReview.builder()
                .partyId(thirdParty.getId())
                .reviewerId(firstHost.getId())
                .revieweeId(secondHost.getId())
                .rating(4)
                .comment("solid host")
                .build());

        userRepository.flush();
        partyRepository.flush();
        partyReviewRepository.flush();

        Statistics statistics = HibernateQueryCountSupport.reset(entityManagerFactory);

        Page<PartyDTO.PublicResponse> page = partyService.getAllParties(
                null,
                null,
                null,
                null,
                PageRequest.of(0, 10),
                null,
                null);

        assertThat(page.getContent()).hasSize(3);
        assertThat(page.getContent())
                .extracting(PartyDTO.PublicResponse::getHostHandle)
                .containsExactly(firstHost.getHandle(), firstHost.getHandle(), secondHost.getHandle());
        assertThat(page.getContent())
                .extracting(PartyDTO.PublicResponse::getHostReviewCount)
                .containsExactly(1L, 1L, 1L);
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(5);
    }

    @Test
    @DisplayName("public party list API keeps query count bounded across multiple parties")
    void getAllPartiesApi_keepsPrepareStatementCountBoundedAcrossMultipleParties() throws Exception {
        UserEntity firstHost = userRepository.save(MateTestFixtureFactory.user("api-host-one@example.com", "API Host One"));
        UserEntity secondHost = userRepository.save(MateTestFixtureFactory.user("api-host-two@example.com", "API Host Two"));

        Party firstParty = partyRepository.save(buildParty(firstHost, "첫 번째 API 파티", LocalDate.of(2026, 5, 1), LocalTime.of(18, 30)));
        partyRepository.save(buildParty(firstHost, "두 번째 API 파티", LocalDate.of(2026, 5, 2), LocalTime.of(18, 30)));
        Party thirdParty = partyRepository.save(buildParty(secondHost, "세 번째 API 파티", LocalDate.of(2026, 5, 3), LocalTime.of(18, 30)));

        partyReviewRepository.save(PartyReview.builder()
                .partyId(firstParty.getId())
                .reviewerId(secondHost.getId())
                .revieweeId(firstHost.getId())
                .rating(5)
                .comment("great api host")
                .build());
        partyReviewRepository.save(PartyReview.builder()
                .partyId(thirdParty.getId())
                .reviewerId(firstHost.getId())
                .revieweeId(secondHost.getId())
                .rating(4)
                .comment("solid api host")
                .build());

        userRepository.flush();
        partyRepository.flush();
        partyReviewRepository.flush();

        Statistics statistics = HibernateQueryCountSupport.reset(entityManagerFactory);

        mockMvc.perform(get("/api/parties")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.content[0].hostHandle").isNotEmpty())
                .andExpect(jsonPath("$.content[0].hostReviewCount").value(1));

        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(5);
    }

    private Party buildParty(UserEntity host, String description, LocalDate gameDate, LocalTime gameTime) {
        Party party = MateTestFixtureFactory.pendingParty(host.getId(), host.getName(), 4);
        party.setHostProfileImageUrl(null);
        party.setGameDate(gameDate);
        party.setGameTime(gameTime);
        party.setDescription(description);
        party.setSearchText("잠실 LG OB 1루 " + host.getName() + " " + description);
        return party;
    }
}
