package com.example.homepage;

import static com.example.common.config.CacheConfig.HOME_BOOTSTRAP;
import static com.example.common.config.CacheConfig.HOME_WIDGETS;

import com.example.cheerboard.dto.PostSummaryRes;
import com.example.cheerboard.service.CheerService;
import com.example.mate.service.PartyService;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HomePageFacadeService {

    private final HomePageGameService homePageGameService;
    private final CheerService cheerService;
    private final PartyService partyService;

    @Cacheable(value = HOME_BOOTSTRAP, key = "#date.toString()")
    @Transactional(readOnly = true)
    public HomeBootstrapResponseDto getBootstrap(LocalDate date) {
        LeagueStartDatesDto leagueStartDates = homePageGameService.getLeagueStartDates();
        HomeRankingSnapshotDto rankingSnapshot = resolveRankingSnapshot(date, leagueStartDates);

        return HomeBootstrapResponseDto.builder()
                .selectedDate(date.toString())
                .leagueStartDates(leagueStartDates)
                .navigation(toHomeScheduleNavigation(homePageGameService.getScheduleNavigation(date)))
                .games(homePageGameService.getGamesByDate(date))
                .scheduledGamesWindow(homePageGameService.getScheduledGamesWindow(date, date.plusDays(7)))
                .rankingSeasonYear(rankingSnapshot.getRankingSeasonYear())
                .rankingSourceMessage(rankingSnapshot.getRankingSourceMessage())
                .isOffSeason(rankingSnapshot.isOffSeason())
                .rankings(rankingSnapshot.getRankings())
                .build();
    }

    @Cacheable(value = HOME_WIDGETS, key = "#date.toString()")
    @Transactional(readOnly = true)
    public HomeWidgetsResponseDto getWidgets(LocalDate date) {
        LocalDate upcomingBaseDate = LocalDate.now();
        List<PostSummaryRes> hotPosts = cheerService
                .getHotPostsPublic(PageRequest.of(0, 3), "HYBRID")
                .getContent();

        return HomeWidgetsResponseDto.builder()
                .hotCheerPosts(hotPosts)
                .featuredMates(partyService.getFeaturedMateCards(upcomingBaseDate, 4))
                .build();
    }

    private HomeRankingSnapshotDto resolveRankingSnapshot(LocalDate selectedDate, LeagueStartDatesDto startDates) {
        boolean isOffSeason = isOffSeason(selectedDate, startDates);
        int baseSeasonYear = isOffSeason ? selectedDate.getYear() - 1 : selectedDate.getYear();

        List<HomePageTeamRankingDto> baseRankings = homePageGameService.getTeamRankings(baseSeasonYear);
        if (!baseRankings.isEmpty()) {
            return HomeRankingSnapshotDto.builder()
                    .rankingSeasonYear(baseSeasonYear)
                    .rankingSourceMessage(baseSeasonYear + " 시즌 순위 데이터")
                    .isOffSeason(isOffSeason)
                    .rankings(baseRankings)
                    .build();
        }

        if (!isOffSeason) {
            return HomeRankingSnapshotDto.builder()
                    .rankingSeasonYear(baseSeasonYear)
                    .rankingSourceMessage(baseSeasonYear + " 시즌 데이터가 아직 집계되지 않았습니다.")
                    .isOffSeason(false)
                    .rankings(List.of())
                    .build();
        }

        int fallbackSeasonYear = baseSeasonYear - 1;
        List<HomePageTeamRankingDto> fallbackRankings = homePageGameService.getTeamRankings(fallbackSeasonYear);
        if (!fallbackRankings.isEmpty()) {
            return HomeRankingSnapshotDto.builder()
                    .rankingSeasonYear(fallbackSeasonYear)
                    .rankingSourceMessage(fallbackSeasonYear + " 시즌 순위 데이터")
                    .isOffSeason(true)
                    .rankings(fallbackRankings)
                    .build();
        }

        return HomeRankingSnapshotDto.builder()
                .rankingSeasonYear(baseSeasonYear)
                .rankingSourceMessage("현재 시즌과 전시즌(전년도) 데이터가 없습니다.")
                .isOffSeason(true)
                .rankings(List.of())
                .build();
    }

    private boolean isOffSeason(LocalDate targetDate, LeagueStartDatesDto startDates) {
        if (targetDate == null) {
            return false;
        }

        LocalDate normalizedStartDate = parseRegularSeasonStart(targetDate, startDates);
        if (normalizedStartDate == null) {
            int month = targetDate.getMonthValue();
            int day = targetDate.getDayOfMonth();
            return month >= 11 || month <= 2 || (month == 3 && day < 22);
        }

        int month = targetDate.getMonthValue();
        return month >= 11 || month <= 2 || targetDate.isBefore(normalizedStartDate);
    }

    private LocalDate parseRegularSeasonStart(LocalDate targetDate, LeagueStartDatesDto startDates) {
        if (targetDate == null || startDates == null || startDates.getRegularSeasonStart() == null) {
            return null;
        }

        try {
            LocalDate parsed = LocalDate.parse(startDates.getRegularSeasonStart());
            int targetYear = targetDate.getYear();
            int month = parsed.getMonthValue();
            int day = Math.min(parsed.getDayOfMonth(), parsed.getMonth().length(Year.isLeap(targetYear)));
            return LocalDate.of(targetYear, month, day);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private HomeScheduleNavigationDto toHomeScheduleNavigation(ScheduleNavigationDto navigation) {
        if (navigation == null) {
            return HomeScheduleNavigationDto.builder()
                    .prevGameDate(null)
                    .nextGameDate(null)
                    .hasPrev(false)
                    .hasNext(false)
                    .build();
        }

        return HomeScheduleNavigationDto.builder()
                .prevGameDate(navigation.getPrevGameDate() == null ? null : navigation.getPrevGameDate().toString())
                .nextGameDate(navigation.getNextGameDate() == null ? null : navigation.getNextGameDate().toString())
                .hasPrev(navigation.isHasPrev())
                .hasNext(navigation.isHasNext())
                .build();
    }
}
