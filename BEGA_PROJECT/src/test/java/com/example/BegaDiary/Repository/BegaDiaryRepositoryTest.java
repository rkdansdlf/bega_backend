package com.example.BegaDiary.Repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import jakarta.persistence.EntityManager;

import com.example.BegaDiary.Entity.BegaDiary;
import com.example.auth.entity.UserEntity;
import com.example.kbo.entity.GameEntity;
import com.example.kbo.entity.TeamEntity;

@DataJpaTest
@DisplayName("BegaDiaryRepository tests")
class BegaDiaryRepositoryTest {

    @Autowired
    private BegaDiaryRepository diaryRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("statistics projection rows map diary and game fields in diary date order")
    void findStatisticsRowsByUserIdOrderByDiaryDateDesc_mapsProjectionFields() {
        TeamEntity favoriteTeam = persistTeam("LG");
        UserEntity user = persistUser("diary-projection@example.test", favoriteTeam);
        GameEntity firstGame = persistGame("DIARY-PROJECTION-1", LocalDate.of(2026, 4, 1), "LG", "KT");
        GameEntity secondGame = persistGame("DIARY-PROJECTION-2", LocalDate.of(2026, 4, 2), "SSG", "LG");

        persistDiary(user, firstGame, LocalDate.of(2026, 4, 1), BegaDiary.DiaryWinning.WIN, "잠실", BegaDiary.DiaryEmoji.BEST);
        persistDiary(user, secondGame, LocalDate.of(2026, 4, 2), BegaDiary.DiaryWinning.LOSE, "문학", BegaDiary.DiaryEmoji.ANGRY);
        entityManager.flush();
        entityManager.clear();

        List<DiaryStatisticsRow> rows = diaryRepository.findStatisticsRowsByUserIdOrderByDiaryDateDesc(user.getId());

        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(DiaryStatisticsRow::getDiaryDate)
                .containsExactly(LocalDate.of(2026, 4, 2), LocalDate.of(2026, 4, 1));
        assertThat(rows.get(0).getWinning()).isEqualTo(BegaDiary.DiaryWinning.LOSE);
        assertThat(rows.get(0).getStadium()).isEqualTo("문학");
        assertThat(rows.get(0).getMood()).isEqualTo(BegaDiary.DiaryEmoji.ANGRY);
        assertThat(rows.get(0).getHomeTeam()).isEqualTo("SSG");
        assertThat(rows.get(0).getAwayTeam()).isEqualTo("LG");
        assertThat(rows.get(0).getFavoriteTeamId()).isEqualTo("LG");
        assertThat(rows.get(1).getWinning()).isEqualTo(BegaDiary.DiaryWinning.WIN);
        assertThat(rows.get(1).getHomeTeam()).isEqualTo("LG");
        assertThat(rows.get(1).getAwayTeam()).isEqualTo("KT");
        assertThat(rows.get(1).getFavoriteTeamId()).isEqualTo("LG");
    }

    private TeamEntity persistTeam(String teamId) {
        TeamEntity team = TeamEntity.builder()
                .teamId(teamId)
                .teamName(teamId + " Twins")
                .teamShortName(teamId)
                .city("Seoul")
                .stadiumName("Jamsil")
                .color("#c30452")
                .build();
        entityManager.persist(team);
        return team;
    }

    private UserEntity persistUser(String email, TeamEntity favoriteTeam) {
        UserEntity user = UserEntity.builder()
                .uniqueId(UUID.randomUUID())
                .handle("@" + UUID.randomUUID().toString().replace("-", "").substring(0, 8))
                .name("Diary Projection User")
                .email(email)
                .password("encoded-password")
                .role("ROLE_USER")
                .provider("LOCAL")
                .favoriteTeam(favoriteTeam)
                .createdAt(LocalDateTime.now())
                .build();
        entityManager.persist(user);
        return user;
    }

    private GameEntity persistGame(String gameId, LocalDate gameDate, String homeTeam, String awayTeam) {
        GameEntity game = GameEntity.builder()
                .gameId(gameId)
                .gameDate(gameDate)
                .stadium("잠실")
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

    private void persistDiary(
            UserEntity user,
            GameEntity game,
            LocalDate diaryDate,
            BegaDiary.DiaryWinning winning,
            String stadium,
            BegaDiary.DiaryEmoji mood) {
        BegaDiary diary = BegaDiary.builder()
                .diaryDate(diaryDate)
                .game(game)
                .memo("projection test diary")
                .mood(mood)
                .type(BegaDiary.DiaryType.ATTENDED)
                .winning(winning)
                .photoUrls(List.of())
                .user(user)
                .team(game.getHomeTeam() + " vs " + game.getAwayTeam())
                .stadium(stadium)
                .section("1루")
                .block("101")
                .seatRow("A")
                .seatNumber("1")
                .build();
        entityManager.persist(diary);
    }
}
