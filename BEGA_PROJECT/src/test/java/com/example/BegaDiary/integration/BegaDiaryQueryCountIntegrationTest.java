package com.example.BegaDiary.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.BegaDiary.Entity.BegaDiary;
import com.example.BegaDiary.Entity.DiaryResponseDto;
import com.example.BegaDiary.Entity.DiaryStatisticsDto;
import com.example.BegaDiary.Repository.BegaDiaryRepository;
import com.example.BegaDiary.Service.BegaDiaryService;
import com.example.BegaDiary.Service.BegaGameService;
import com.example.BegaDiary.Service.SeatViewService;
import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserRepository;
import com.example.cheerboard.domain.CheerPost;
import com.example.cheerboard.domain.PostType;
import com.example.cheerboard.repo.CheerPostRepo;
import com.example.cheerboard.storage.service.ImageService;
import com.example.kbo.entity.GameEntity;
import com.example.kbo.entity.TeamEntity;
import com.example.kbo.service.TicketVerificationTokenStore;
import com.example.mate.entity.Party;
import com.example.mate.entity.PartyApplication;
import com.example.mate.repository.PartyApplicationRepository;
import com.example.media.service.MediaLinkService;
import com.example.support.HibernateQueryCountSupport;
import com.example.support.HibernateStatisticsTestConfig;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import reactor.core.publisher.Mono;

@DataJpaTest
@Import(HibernateStatisticsTestConfig.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:diary_query_count;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
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
class BegaDiaryQueryCountIntegrationTest {

    private static final long ENTRIES_MAX_QUERIES = 3;
    private static final long STATISTICS_MAX_QUERIES = 3;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private BegaDiaryRepository diaryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CheerPostRepo cheerPostRepository;

    @Autowired
    private PartyApplicationRepository partyApplicationRepository;

    private BegaDiaryService diaryService;

    @BeforeEach
    void setUp() {
        ImageService imageService = mock(ImageService.class);
        BegaGameService gameService = mock(BegaGameService.class);
        TicketVerificationTokenStore ticketVerificationTokenStore = mock(TicketVerificationTokenStore.class);
        SeatViewService seatViewService = mock(SeatViewService.class);
        MediaLinkService mediaLinkService = mock(MediaLinkService.class);

        when(imageService.getDiaryImageSignedUrls(anyList(), any(), any()))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        diaryService = new BegaDiaryService(
                diaryRepository,
                gameService,
                userRepository,
                imageService,
                cheerPostRepository,
                partyApplicationRepository,
                ticketVerificationTokenStore,
                seatViewService,
                mediaLinkService);
    }

    @Test
    @DisplayName("diary entries query count stays bounded when loading batched photo URLs")
    void entries_keepsPrepareStatementCountBoundedForDiaryPhotos() {
        UserEntity user = persistUser("diary-entries@example.test", "entries");
        seedDiaries(user, 6);
        entityManager.flush();
        entityManager.clear();

        Statistics statistics = HibernateQueryCountSupport.reset(entityManagerFactory);

        List<DiaryResponseDto> entries = diaryService.getAllDiaries(user.getId());

        assertThat(entries).hasSize(6);
        assertThat(entries)
                .allSatisfy(entry -> assertThat(entry.getPhotos()).hasSize(2));
        assertThat(statistics.getPrepareStatementCount())
                .as("observed diary entries queries")
                .isLessThanOrEqualTo(ENTRIES_MAX_QUERIES);
    }

    @Test
    @DisplayName("diary statistics query count stays bounded for projection and aggregate counts")
    void statistics_keepsPrepareStatementCountBoundedForProjectionAndCounts() {
        UserEntity user = persistUser("diary-statistics@example.test", "stats");
        seedDiaries(user, 4);
        TeamEntity team = persistTeam();
        persistCheerPost(user, team, "stats-post-1");
        persistCheerPost(user, team, "stats-post-2");
        persistCheckedInPartyParticipation(user);
        entityManager.flush();
        entityManager.clear();

        Statistics statistics = HibernateQueryCountSupport.reset(entityManagerFactory);

        DiaryStatisticsDto result = diaryService.getStatistics(user.getId());

        assertThat(result.getTotalCount()).isEqualTo(4);
        assertThat(result.getCheerPostCount()).isEqualTo(2);
        assertThat(result.getMateParticipationCount()).isEqualTo(1);
        assertThat(statistics.getPrepareStatementCount())
                .as("observed diary statistics queries")
                .isLessThanOrEqualTo(STATISTICS_MAX_QUERIES);
    }

    private void seedDiaries(UserEntity user, int count) {
        for (int index = 0; index < count; index++) {
            LocalDate gameDate = LocalDate.of(2026, 4, 1).plusDays(index);
            GameEntity game = persistGame("DIARY-QC-" + index, gameDate, index % 2 == 0 ? "LG" : "KT",
                    index % 2 == 0 ? "KT" : "LG");
            persistDiary(user, game, gameDate, index);
        }
    }

    private UserEntity persistUser(String email, String handle) {
        UserEntity user = UserEntity.builder()
                .uniqueId(UUID.randomUUID())
                .handle(handle)
                .name(handle)
                .email(email)
                .password("encoded-password")
                .role("ROLE_USER")
                .provider("LOCAL")
                .build();
        entityManager.persist(user);
        return user;
    }

    private GameEntity persistGame(String gameId, LocalDate gameDate, String homeTeam, String awayTeam) {
        GameEntity game = GameEntity.builder()
                .gameId(gameId)
                .gameDate(gameDate)
                .stadium("Jamsil")
                .homeTeam(homeTeam)
                .awayTeam(awayTeam)
                .homeScore(4)
                .awayScore(2)
                .winningTeam(homeTeam)
                .winningScore(4)
                .seasonId(2026)
                .stadiumId("JAMSIL")
                .gameStatus("COMPLETED")
                .isDummy(false)
                .build();
        entityManager.persist(game);
        return game;
    }

    private void persistDiary(UserEntity user, GameEntity game, LocalDate diaryDate, int index) {
        BegaDiary diary = BegaDiary.builder()
                .diaryDate(diaryDate)
                .game(game)
                .memo("query count diary " + index)
                .mood(index % 2 == 0 ? BegaDiary.DiaryEmoji.BEST : BegaDiary.DiaryEmoji.HAPPY)
                .type(BegaDiary.DiaryType.ATTENDED)
                .winning(index % 3 == 0 ? BegaDiary.DiaryWinning.LOSE : BegaDiary.DiaryWinning.WIN)
                .photoUrls(List.of("diary/" + index + "/first.jpg", "diary/" + index + "/second.jpg"))
                .user(user)
                .team(game.getHomeTeam() + " vs " + game.getAwayTeam())
                .stadium(game.getStadium())
                .section("101")
                .block("A")
                .seatRow("1")
                .seatNumber(String.valueOf(index + 1))
                .build();
        entityManager.persist(diary);
    }

    private TeamEntity persistTeam() {
        TeamEntity team = TeamEntity.builder()
                .teamId("LG")
                .teamName("LG Twins")
                .teamShortName("LG")
                .city("Seoul")
                .stadiumName("Jamsil")
                .color("#c30452")
                .build();
        entityManager.persist(team);
        return team;
    }

    private void persistCheerPost(UserEntity author, TeamEntity team, String content) {
        CheerPost post = CheerPost.builder()
                .team(team)
                .postType(PostType.NORMAL)
                .author(author)
                .content(content)
                .likeCount(0)
                .commentCount(0)
                .views(0)
                .repostCount(0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .deleted(false)
                .build();
        entityManager.persist(post);
    }

    private void persistCheckedInPartyParticipation(UserEntity user) {
        UserEntity host = persistUser("diary-party-host@example.test", "host");
        Party party = Party.builder()
                .hostId(host.getId())
                .hostName(host.getName())
                .hostBadge(Party.BadgeType.NEW)
                .teamId("LG")
                .gameDate(LocalDate.of(2026, 4, 10))
                .gameTime(LocalTime.of(18, 30))
                .stadium("Jamsil")
                .homeTeam("LG")
                .awayTeam("KT")
                .section("101")
                .maxParticipants(4)
                .currentParticipants(2)
                .description("query count party")
                .ticketVerified(false)
                .status(Party.PartyStatus.CHECKED_IN)
                .build();
        entityManager.persist(party);
        entityManager.flush();

        PartyApplication application = PartyApplication.builder()
                .partyId(party.getId())
                .applicantId(user.getId())
                .applicantName(user.getName())
                .applicantBadge(Party.BadgeType.NEW)
                .applicantRating(5.0)
                .message("query count application")
                .depositAmount(0)
                .isPaid(false)
                .isApproved(true)
                .isRejected(false)
                .paymentType(PartyApplication.PaymentType.DEPOSIT)
                .approvedAt(Instant.now())
                .build();
        entityManager.persist(application);
    }
}
