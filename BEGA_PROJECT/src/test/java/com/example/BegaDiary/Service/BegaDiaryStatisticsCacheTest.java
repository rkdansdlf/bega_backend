package com.example.BegaDiary.Service;

import com.example.BegaDiary.Entity.BegaDiary;
import com.example.BegaDiary.Entity.DiaryStatisticsDto;
import com.example.BegaDiary.Repository.BegaDiaryRepository;
import com.example.BegaDiary.Repository.DiaryStatisticsRow;
import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserRepository;
import com.example.cheerboard.repo.CheerPostRepo;
import com.example.cheerboard.storage.service.ImageService;
import com.example.kbo.entity.TeamEntity;
import com.example.common.config.CacheConfig;
import com.example.kbo.service.TicketVerificationTokenStore;
import com.example.mate.repository.PartyApplicationRepository;
import com.example.media.service.MediaLinkService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(classes = {CacheConfig.class, BegaDiaryStatisticsCacheTest.TestConfig.class})
@DisplayName("BegaDiary statistics cache tests")
class BegaDiaryStatisticsCacheTest {

    @jakarta.annotation.Resource
    private BegaDiaryService diaryService;

    @jakarta.annotation.Resource
    private BegaDiaryRepository diaryRepository;

    @jakarta.annotation.Resource
    private UserRepository userRepository;

    @jakarta.annotation.Resource
    private CheerPostRepo cheerPostRepository;

    @jakarta.annotation.Resource
    private PartyApplicationRepository partyApplicationRepository;

    @jakarta.annotation.Resource
    private CacheManager cacheManager;

    @BeforeEach
    void resetMocksAndCache() {
        Cache cache = cacheManager.getCache(CacheConfig.DIARY_STATS);
        if (cache != null) {
            cache.clear();
        }
        reset(diaryRepository, userRepository, cheerPostRepository, partyApplicationRepository);
    }

    @Test
    @DisplayName("diaryStats is backed by Caffeine L1 cache")
    void diaryStatsCache_isCaffeineBacked() {
        Cache cache = cacheManager.getCache(CacheConfig.DIARY_STATS);

        assertThat(cache).isInstanceOf(CaffeineCache.class);
    }

    @Test
    @DisplayName("getStatistics caches repeated user lookups and delete evicts the entry")
    void getStatistics_cachesAndDeleteEvicts() {
        Long userId = 10L;
        BegaDiary diary = createDiary(100L, userId, LocalDate.of(2026, 3, 9));
        DiaryStatisticsRow row = statsRow(
                LocalDate.of(2026, 3, 9),
                BegaDiary.DiaryWinning.WIN,
                "잠실",
                BegaDiary.DiaryEmoji.HAPPY,
                "LG",
                "KT");
        when(diaryRepository.findStatisticsRowsByUserIdOrderByDiaryDateDesc(userId))
                .thenReturn(List.of(row), List.of());
        when(diaryRepository.findByIdAndUserId(100L, userId)).thenReturn(Optional.of(diary));
        when(userRepository.findById(userId)).thenReturn(Optional.empty());
        when(cheerPostRepository.countByUserId(userId)).thenReturn(0);
        when(partyApplicationRepository.countCheckedInPartiesByUserId(userId)).thenReturn(0);

        DiaryStatisticsDto first = diaryService.getStatistics(userId);
        DiaryStatisticsDto second = diaryService.getStatistics(userId);

        assertThat(first.getTotalCount()).isEqualTo(1);
        assertThat(second.getTotalCount()).isEqualTo(1);
        verify(diaryRepository, times(1)).findStatisticsRowsByUserIdOrderByDiaryDateDesc(userId);
        verify(diaryRepository, never()).findByUserIdOrderByDiaryDateDesc(userId);

        diaryService.delete(100L, userId);
        DiaryStatisticsDto afterDelete = diaryService.getStatistics(userId);

        assertThat(afterDelete.getTotalCount()).isZero();
        verify(diaryRepository, times(2)).findStatisticsRowsByUserIdOrderByDiaryDateDesc(userId);
    }

    @Test
    @DisplayName("getStatistics calculates DTO fields from projection rows")
    void getStatistics_usesProjectionRowsForOutputFields() {
        Long userId = 20L;
        LocalDate currentMonthStart = LocalDate.now().withDayOfMonth(1);
        List<DiaryStatisticsRow> rows = List.of(
                statsRow(
                        currentMonthStart.plusDays(2),
                        BegaDiary.DiaryWinning.DRAW,
                        "대구",
                        BegaDiary.DiaryEmoji.BEST,
                        "LG",
                        "KT"),
                statsRow(
                        currentMonthStart.plusDays(3),
                        null,
                        "수원",
                        BegaDiary.DiaryEmoji.BEST,
                        "KT",
                        "LG",
                        BegaDiary.DiaryType.SCHEDULED),
                statsRow(
                        currentMonthStart.plusDays(1),
                        BegaDiary.DiaryWinning.WIN,
                        "잠실",
                        BegaDiary.DiaryEmoji.HAPPY,
                        "KT",
                        "LG"),
                statsRow(
                        currentMonthStart,
                        BegaDiary.DiaryWinning.WIN,
                        "잠실",
                        BegaDiary.DiaryEmoji.BEST,
                        "LG",
                        "KT"),
                statsRow(
                        currentMonthStart.minusYears(1),
                        BegaDiary.DiaryWinning.LOSE,
                        "고척",
                        BegaDiary.DiaryEmoji.BEST,
                        "LG",
                        "SS"));

        when(diaryRepository.findStatisticsRowsByUserIdOrderByDiaryDateDesc(userId)).thenReturn(rows);
        when(userRepository.findById(userId)).thenReturn(Optional.of(userWithFavoriteTeam(userId, "LG")));
        when(cheerPostRepository.countByUserId(userId)).thenReturn(3);
        when(partyApplicationRepository.countCheckedInPartiesByUserId(userId)).thenReturn(2);

        DiaryStatisticsDto statistics = diaryService.getStatistics(userId);

        assertThat(statistics.getTotalCount()).isEqualTo(4);
        assertThat(statistics.getTotalWins()).isEqualTo(2);
        assertThat(statistics.getTotalLosses()).isEqualTo(1);
        assertThat(statistics.getTotalDraws()).isEqualTo(1);
        assertThat(statistics.getWinRate()).isEqualTo(50.0);
        assertThat(statistics.getMonthlyCount()).isEqualTo(3);
        assertThat(statistics.getYearlyCount()).isEqualTo(3);
        assertThat(statistics.getYearlyWins()).isEqualTo(2);
        assertThat(statistics.getYearlyWinRate()).isEqualTo(66.7);
        assertThat(statistics.getMostVisitedStadium()).isEqualTo("잠실 야구장");
        assertThat(statistics.getMostVisitedCount()).isEqualTo(2);
        assertThat(statistics.getMonthlyVisitCounts())
                .containsEntry(currentMonthStart.getMonthValue(), 3)
                .doesNotContainEntry(currentMonthStart.getMonthValue(), 4);
        assertThat(statistics.getStadiumVisitCounts())
                .containsEntry("잠실 야구장", 2)
                .containsEntry("대구 삼성 라이온즈 파크", 1)
                .containsEntry("고척스카이돔", 1);
        assertThat(statistics.getHomeVisitCount()).isEqualTo(2);
        assertThat(statistics.getAwayVisitCount()).isEqualTo(1);
        assertThat(statistics.getScheduledCount()).isEqualTo(1);
        assertThat(statistics.getHappiestMonth()).isEqualTo(currentMonthStart.getMonthValue() + "월");
        assertThat(statistics.getHappiestCount()).isEqualTo(3);
        assertThat(statistics.getFirstDiaryDate()).isEqualTo(currentMonthStart.minusYears(1).toString());
        assertThat(statistics.getCheerPostCount()).isEqualTo(3);
        assertThat(statistics.getMateParticipationCount()).isEqualTo(2);
        assertThat(statistics.getEmojiCounts()).containsEntry("최고", 3L).containsEntry("즐거움", 1L);
        assertThat(statistics.getCurrentWinStreak()).isZero();
        assertThat(statistics.getCurrentLossStreak()).isZero();
        assertThat(statistics.getLongestWinStreak()).isEqualTo(2);
        assertThat(statistics.getOpponentWinRates()).containsKey("KT 위즈");
        DiaryStatisticsDto.OpponentStats ktStats = statistics.getOpponentWinRates().get("KT 위즈");
        assertThat(ktStats.getWins()).isEqualTo(2);
        assertThat(ktStats.getDraws()).isEqualTo(1);
        assertThat(Math.round(ktStats.getWinRate() * 10) / 10.0).isEqualTo(66.7);
        assertThat(statistics.getBestOpponent()).isEqualTo("KT 위즈");
        assertThat(statistics.getWorstOpponent()).isEqualTo("KT 위즈");
        assertThat(statistics.getDayOfWeekStats().values().stream().mapToInt(DiaryStatisticsDto.DayStats::getCount).sum())
                .isEqualTo(4);
        assertThat(statistics.getEarnedBadges()).containsExactly("ticket", "map-pin");
        verify(diaryRepository, times(1)).findStatisticsRowsByUserIdOrderByDiaryDateDesc(userId);
        verify(diaryRepository, never()).findByUserIdOrderByDiaryDateDesc(userId);
    }

    private static BegaDiary createDiary(Long diaryId, Long ownerId, LocalDate date) {
        UserEntity owner = UserEntity.builder()
                .id(ownerId)
                .email("owner@test.com")
                .name("Owner")
                .build();

        BegaDiary diary = BegaDiary.builder()
                .diaryDate(date)
                .memo("직관 기록")
                .mood(BegaDiary.DiaryEmoji.HAPPY)
                .type(BegaDiary.DiaryType.ATTENDED)
                .winning(BegaDiary.DiaryWinning.WIN)
                .photoUrls(List.of())
                .user(owner)
                .team("LG vs KT")
                .stadium("잠실야구장")
                .section("1루")
                .block("101")
                .seatRow("A")
                .seatNumber("1")
                .build();
        ReflectionTestUtils.setField(diary, "id", diaryId);
        return diary;
    }

    private static UserEntity userWithFavoriteTeam(Long userId, String teamId) {
        return UserEntity.builder()
                .id(userId)
                .email("owner@test.com")
                .name("Owner")
                .favoriteTeam(TeamEntity.builder()
                        .teamId(teamId)
                        .teamName(teamId)
                        .teamShortName(teamId)
                        .city("서울")
                        .build())
                .build();
    }

    private static DiaryStatisticsRow statsRow(
            LocalDate diaryDate,
            BegaDiary.DiaryWinning winning,
            String stadium,
            BegaDiary.DiaryEmoji mood,
            String homeTeam,
            String awayTeam) {
        return statsRow(diaryDate, winning, stadium, mood, homeTeam, awayTeam, BegaDiary.DiaryType.ATTENDED);
    }

    private static DiaryStatisticsRow statsRow(
            LocalDate diaryDate,
            BegaDiary.DiaryWinning winning,
            String stadium,
            BegaDiary.DiaryEmoji mood,
            String homeTeam,
            String awayTeam,
            BegaDiary.DiaryType type) {
        return new TestDiaryStatisticsRow(diaryDate, winning, type, stadium, mood, homeTeam, awayTeam);
    }

    private record TestDiaryStatisticsRow(
            LocalDate diaryDate,
            BegaDiary.DiaryWinning winning,
            BegaDiary.DiaryType type,
            String stadium,
            BegaDiary.DiaryEmoji mood,
            String homeTeam,
            String awayTeam) implements DiaryStatisticsRow {

        @Override
        public LocalDate getDiaryDate() {
            return diaryDate;
        }

        @Override
        public BegaDiary.DiaryWinning getWinning() {
            return winning;
        }

        @Override
        public BegaDiary.DiaryType getType() {
            return type;
        }

        @Override
        public String getStadium() {
            return stadium;
        }

        @Override
        public BegaDiary.DiaryEmoji getMood() {
            return mood;
        }

        @Override
        public String getHomeTeam() {
            return homeTeam;
        }

        @Override
        public String getAwayTeam() {
            return awayTeam;
        }
    }

    @Configuration
    static class TestConfig {

        @Bean
        GenericJackson2JsonRedisSerializer redisValueSerializer() {
            return new GenericJackson2JsonRedisSerializer(new ObjectMapper());
        }

        @Bean
        RedisConnectionFactory redisConnectionFactory() {
            return mock(RedisConnectionFactory.class);
        }

        @Bean
        BegaDiaryRepository diaryRepository() {
            return mock(BegaDiaryRepository.class);
        }

        @Bean
        BegaGameService gameService() {
            return mock(BegaGameService.class);
        }

        @Bean
        UserRepository userRepository() {
            return mock(UserRepository.class);
        }

        @Bean
        ImageService imageService() {
            return mock(ImageService.class);
        }

        @Bean
        CheerPostRepo cheerPostRepository() {
            return mock(CheerPostRepo.class);
        }

        @Bean
        PartyApplicationRepository partyApplicationRepository() {
            return mock(PartyApplicationRepository.class);
        }

        @Bean
        TicketVerificationTokenStore ticketVerificationTokenStore() {
            return mock(TicketVerificationTokenStore.class);
        }

        @Bean
        SeatViewService seatViewService() {
            return mock(SeatViewService.class);
        }

        @Bean
        MediaLinkService mediaLinkService() {
            return mock(MediaLinkService.class);
        }

        @Bean
        BegaDiaryService diaryService(
                BegaDiaryRepository diaryRepository,
                BegaGameService gameService,
                UserRepository userRepository,
                ImageService imageService,
                CheerPostRepo cheerPostRepository,
                PartyApplicationRepository partyApplicationRepository,
                TicketVerificationTokenStore ticketVerificationTokenStore,
                SeatViewService seatViewService,
                MediaLinkService mediaLinkService) {
            return new BegaDiaryService(
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
    }
}
