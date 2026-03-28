package com.example.homepage;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/kbo")
@RequiredArgsConstructor
@Slf4j
public class HomePageController {

    private final HomePageGameService homePageGameService;
    private final HomePageFacadeService homePageFacadeService;

    // 특정 날짜의 KBO 경기 목록을 조회
    @GetMapping("/schedule")
    public ResponseEntity<List<HomePageGameDto>> getGamesByDate(
            // @RequestParam으로 "date" 파라미터를 받고, ISO_DATE 형식(YYYY-MM-DD)으로 LocalDate 변환
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return respondWithFallback(
                () -> homePageGameService.getGamesByDate(date),
                List::of,
                "KBO schedule",
                "date=" + date);
    }

    // 특정 시즌의 팀 순위 조회
    @GetMapping("/rankings/{seasonYear}")
    public ResponseEntity<List<HomePageTeamRankingDto>> getTeamRankings(
            @PathVariable int seasonYear) {
        return respondWithFallback(
                () -> homePageGameService.getTeamRankings(seasonYear),
                List::of,
                "KBO rankings",
                "seasonYear=" + seasonYear);
    }

    @GetMapping("/rankings/snapshot")
    public ResponseEntity<HomeRankingSnapshotDto> getRankingSnapshot(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Integer seasonYear) {
        LocalDate selectedDate = date == null ? LocalDate.now() : date;
        try {
            return ResponseEntity.ok(homePageFacadeService.getRankingSnapshot(selectedDate, seasonYear));
        } catch (RuntimeException ex) {
            log.warn("KBO ranking snapshot fallback applied - date={}, seasonYear={}, reason={}", selectedDate, seasonYear, ex.getMessage());
            return ResponseEntity.ok(buildRankingSnapshotFallback(selectedDate, seasonYear));
        }
    }

    // 각 리그별 시즌 시작 날짜를 조회
    @GetMapping("/league-start-dates")
    public ResponseEntity<LeagueStartDatesDto> getLeagueStartDates() {
        return respondWithFallback(
                homePageGameService::getLeagueStartDates,
                this::buildLeagueStartDatesFallback,
                "KBO league start dates",
                "currentDate=" + LocalDate.now());
    }

    // 날짜 네비게이션 정보 조회
    @GetMapping("/schedule/navigation")
    public ResponseEntity<ScheduleNavigationDto> getScheduleNavigation(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return respondWithFallback(
                () -> homePageGameService.getScheduleNavigation(date),
                this::buildScheduleNavigationFallback,
                "KBO schedule navigation",
                "date=" + date);
    }

    private <T> ResponseEntity<T> respondWithFallback(
            Supplier<T> primarySupplier,
            Supplier<T> fallbackSupplier,
            String operation,
            String context) {
        try {
            return ResponseEntity.ok(primarySupplier.get());
        } catch (RuntimeException ex) {
            log.warn("{} fallback applied - {}, reason={}", operation, context, ex.getMessage());
            return ResponseEntity.ok(fallbackSupplier.get());
        }
    }

    private LeagueStartDatesDto buildLeagueStartDatesFallback() {
        LocalDate today = LocalDate.now();
        return LeagueStartDatesDto.builder()
                .regularSeasonStart(today.toString())
                .postseasonStart(today.toString())
                .koreanSeriesStart(today.toString())
                .build();
    }

    private ScheduleNavigationDto buildScheduleNavigationFallback() {
        return ScheduleNavigationDto.builder()
                .prevGameDate(null)
                .nextGameDate(null)
                .hasPrev(false)
                .hasNext(false)
                .build();
    }

    private HomeRankingSnapshotDto buildRankingSnapshotFallback(LocalDate selectedDate, Integer seasonYear) {
        boolean offSeason = seasonYear == null && isAutomaticOffSeason(selectedDate);
        int rankingSeasonYear = seasonYear == null
                ? (offSeason ? selectedDate.getYear() - 1 : selectedDate.getYear())
                : seasonYear;
        return HomeRankingSnapshotDto.builder()
                .rankingSeasonYear(rankingSeasonYear)
                .rankingSourceMessage("순위 데이터를 불러오지 못했습니다.")
                .isOffSeason(offSeason)
                .rankings(List.of())
                .build();
    }

    private boolean isAutomaticOffSeason(LocalDate selectedDate) {
        int month = selectedDate.getMonthValue();
        int day = selectedDate.getDayOfMonth();
        return month >= 11 || month <= 2 || (month == 3 && day < 22);
    }
}
