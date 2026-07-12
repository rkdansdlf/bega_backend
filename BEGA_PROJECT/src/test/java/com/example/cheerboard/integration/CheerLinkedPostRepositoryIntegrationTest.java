package com.example.cheerboard.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import com.example.BegaDiary.Entity.BegaDiary;
import com.example.BegaDiary.Repository.BegaDiaryRepository;
import com.example.auth.entity.UserEntity;
import com.example.cheerboard.domain.CheerPost;
import com.example.cheerboard.domain.PostType;
import com.example.cheerboard.dto.LinkedContentRes;
import com.example.cheerboard.repo.CheerPostRepo;
import com.example.cheerboard.service.CheerLinkedPostService;
import com.example.kbo.entity.GameEntity;
import com.example.kbo.entity.TeamEntity;
import com.example.mate.entity.Party;
import com.example.mate.repository.PartyRepository;
import com.example.support.HibernateQueryCountSupport;
import com.example.support.HibernateStatisticsTestConfig;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

@DataJpaTest
@Import({HibernateStatisticsTestConfig.class, CheerLinkedPostService.class})
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:cheer_linked_repository;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
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
class CheerLinkedPostRepositoryIntegrationTest {

    private static final String PRIVATE_PHOTO_SENTINEL = "PRIVATE_DIARY_PHOTO_SENTINEL";
    private static final String PRIVATE_TICKET_SENTINEL = "PRIVATE_PARTY_TICKET_SENTINEL";

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private BegaDiaryRepository diaryRepository;

    @Autowired
    private PartyRepository partyRepository;

    @Autowired
    private CheerPostRepo postRepository;

    @Autowired
    private CheerLinkedPostService linkedPostService;

    @Test
    void ownerScopedDiaryLookupLoadsOwnerAndGameButNotPhotosInOneStatement() {
        UserEntity owner = persistUser("owner-query");
        UserEntity stranger = persistUser("stranger-query");
        BegaDiary diary = persistDiary(owner, persistGame("OWNER-GAME", LocalDate.of(2026, 7, 13)), "LG");
        flushAndClear();

        Statistics statistics = HibernateQueryCountSupport.reset(entityManagerFactory);

        BegaDiary loaded = diaryRepository.findByIdAndUserIdWithOwnerAndGame(diary.getId(), owner.getId())
                .orElseThrow();

        assertThat(entityManagerFactory.getPersistenceUnitUtil().isLoaded(loaded, "user")).isTrue();
        assertThat(entityManagerFactory.getPersistenceUnitUtil().isLoaded(loaded, "game")).isTrue();
        assertThat(entityManagerFactory.getPersistenceUnitUtil().isLoaded(loaded, "photoUrls")).isFalse();
        assertThat(loaded.getUser().getId()).isEqualTo(owner.getId());
        assertThat(loaded.getGame().getGameId()).isEqualTo("OWNER-GAME");
        assertThat(diaryRepository.findByIdAndUserIdWithOwnerAndGame(diary.getId(), stranger.getId()))
                .isEmpty();
        assertThat(statistics.getPrepareStatementCount())
                .as("owner-scoped diary lookup statements")
                .isLessThanOrEqualTo(2);
    }

    @Test
    void bulkResolutionBuildsMultipleSafePreviewsWithinOneQueryPerSourceType() throws Exception {
        UserEntity owner = persistUser("bulk-owner");
        BegaDiary firstDiary = persistDiary(
                owner, persistGame("BULK-GAME-1", LocalDate.of(2026, 7, 13)), "LG");
        BegaDiary secondDiary = persistDiary(
                owner, persistGame("BULK-GAME-2", LocalDate.of(2026, 7, 14)), "KT");
        Party firstParty = persistParty(owner.getId(), "BULK-PARTY-1");
        Party secondParty = persistParty(owner.getId(), "BULK-PARTY-2");
        flushAndClear();

        Statistics statistics = HibernateQueryCountSupport.reset(entityManagerFactory);

        Map<Long, LinkedContentRes> resolved = linkedPostService.resolveForPosts(List.of(
                linkedPost(101L, PostType.CHECKIN, firstDiary.getId(), null),
                linkedPost(102L, PostType.CHECKIN, secondDiary.getId(), null),
                linkedPost(103L, PostType.RECRUITMENT, null, firstParty.getId()),
                linkedPost(104L, PostType.RECRUITMENT, null, secondParty.getId())));

        assertThat(resolved).containsOnlyKeys(101L, 102L, 103L, 104L);
        assertThat(resolved.get(101L).checkin().homeTeam()).isEqualTo("LG");
        assertThat(resolved.get(102L).checkin().homeTeam()).isEqualTo("KT");
        assertThat(resolved.get(103L).recruitment().description()).isEqualTo("BULK-PARTY-1");
        assertThat(resolved.get(104L).recruitment().description()).isEqualTo("BULK-PARTY-2");
        assertThat(new ObjectMapper().findAndRegisterModules().writeValueAsString(resolved))
                .doesNotContain(
                        PRIVATE_PHOTO_SENTINEL,
                        PRIVATE_TICKET_SENTINEL,
                        "PRIVATE_DIARY_MEMO_SENTINEL",
                        "PRIVATE_SECTION_SENTINEL",
                        "PRIVATE_HOST_SENTINEL",
                        "PRIVATE_PROFILE_SENTINEL",
                        "PRIVATE_SEAT_SENTINEL",
                        "PRIVATE_RESERVATION_SENTINEL");
        assertThat(statistics.getPrepareStatementCount())
                .as("bulk linked preview statements")
                .isLessThanOrEqualTo(2);
    }

    @Test
    void activePostLookupReturnsLiveRowsAndFetchesPublicAssociations() {
        UserEntity author = persistUser("post-author");
        TeamEntity team = persistTeam();
        CheerPost deletedDiaryPost = persistPost(author, team, PostType.CHECKIN, 301L, null, true);
        CheerPost liveDiaryPost = persistPost(author, team, PostType.CHECKIN, 301L, null, false);
        CheerPost deletedPartyPost = persistPost(author, team, PostType.RECRUITMENT, null, 401L, true);
        CheerPost livePartyPost = persistPost(author, team, PostType.RECRUITMENT, null, 401L, false);
        flushAndClear();

        Statistics statistics = HibernateQueryCountSupport.reset(entityManagerFactory);

        CheerPost loadedDiaryPost = postRepository.findFirstByDiaryIdAndDeletedFalse(301L).orElseThrow();
        CheerPost loadedPartyPost = postRepository.findFirstByPartyIdAndDeletedFalse(401L).orElseThrow();

        assertThat(loadedDiaryPost.getId()).isEqualTo(liveDiaryPost.getId()).isNotEqualTo(deletedDiaryPost.getId());
        assertThat(loadedPartyPost.getId()).isEqualTo(livePartyPost.getId()).isNotEqualTo(deletedPartyPost.getId());
        assertThat(entityManagerFactory.getPersistenceUnitUtil().isLoaded(loadedDiaryPost, "author")).isTrue();
        assertThat(entityManagerFactory.getPersistenceUnitUtil().isLoaded(loadedDiaryPost, "team")).isTrue();
        assertThat(loadedDiaryPost.getAuthor().getHandle()).isEqualTo(author.getHandle());
        assertThat(loadedPartyPost.getTeam().getTeamId()).isEqualTo(team.getTeamId());
        assertThat(statistics.getPrepareStatementCount())
                .as("active linked post lookup statements")
                .isLessThanOrEqualTo(2);
    }

    private UserEntity persistUser(String handlePrefix) {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        String handle = handlePrefix.substring(0, Math.min(handlePrefix.length(), 8)) + suffix;
        UserEntity user = UserEntity.builder()
                .uniqueId(UUID.randomUUID())
                .handle(handle)
                .name(handlePrefix)
                .email(handlePrefix + "+" + suffix + "@example.test")
                .password("encoded-password")
                .role("ROLE_USER")
                .provider("LOCAL")
                .build();
        entityManager.persist(user);
        return user;
    }

    private GameEntity persistGame(String gameId, LocalDate gameDate) {
        String homeTeam = gameId.endsWith("1") ? "LG" : gameId.endsWith("2") ? "KT" : "LG";
        GameEntity game = GameEntity.builder()
                .gameId(gameId)
                .gameDate(gameDate)
                .stadium("Jamsil")
                .homeTeam(homeTeam)
                .awayTeam("KIA")
                .seasonId(2026)
                .stadiumId("JAMSIL")
                .gameStatus("COMPLETED")
                .isDummy(false)
                .build();
        entityManager.persist(game);
        return game;
    }

    private BegaDiary persistDiary(UserEntity owner, GameEntity game, String cheeringTeam) {
        BegaDiary diary = BegaDiary.builder()
                .diaryDate(game.getGameDate())
                .game(game)
                .memo("PRIVATE_DIARY_MEMO_SENTINEL")
                .mood(BegaDiary.DiaryEmoji.BEST)
                .type(BegaDiary.DiaryType.ATTENDED)
                .winning(BegaDiary.DiaryWinning.WIN)
                .photoUrls(List.of(PRIVATE_PHOTO_SENTINEL))
                .user(owner)
                .team(cheeringTeam)
                .stadium(game.getStadium())
                .section("PRIVATE_SECTION_SENTINEL")
                .ticketVerified(true)
                .build();
        entityManager.persist(diary);
        return diary;
    }

    private Party persistParty(Long hostId, String description) {
        Party party = Party.builder()
                .hostId(hostId)
                .hostName("PRIVATE_HOST_SENTINEL")
                .hostBadge(Party.BadgeType.VERIFIED)
                .hostProfileImageUrl("PRIVATE_PROFILE_SENTINEL")
                .teamId("LG")
                .gameDate(LocalDate.of(2026, 7, 13))
                .gameTime(LocalTime.of(18, 30))
                .stadium("Jamsil")
                .homeTeam("LG")
                .awayTeam("KIA")
                .section("Blue")
                .seatDetail("PRIVATE_SEAT_SENTINEL")
                .maxParticipants(4)
                .currentParticipants(2)
                .description(description)
                .ticketVerified(true)
                .ticketImageUrl(PRIVATE_TICKET_SENTINEL)
                .status(Party.PartyStatus.PENDING)
                .reservationNumber("PRIVATE_RESERVATION_SENTINEL")
                .build();
        entityManager.persist(party);
        return party;
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

    private CheerPost persistPost(
            UserEntity author,
            TeamEntity team,
            PostType type,
            Long diaryId,
            Long partyId,
            boolean deleted) {
        CheerPost post = CheerPost.builder()
                .author(author)
                .team(team)
                .postType(type)
                .diaryId(diaryId)
                .partyId(partyId)
                .content("linked post")
                .deleted(deleted)
                .build();
        entityManager.persist(post);
        return post;
    }

    private static CheerPost linkedPost(Long id, PostType type, Long diaryId, Long partyId) {
        return CheerPost.builder()
                .id(id)
                .postType(type)
                .diaryId(diaryId)
                .partyId(partyId)
                .build();
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
