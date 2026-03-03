package com.example.homepage;

import java.time.LocalDate;
import java.util.List;

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

    // 특정 날짜의 KBO 경기 목록을 조회
    @GetMapping("/schedule")
    public ResponseEntity<List<HomePageGameDto>> getGamesByDate(
            // @RequestParam으로 "date" 파라미터를 받고, ISO_DATE 형식(YYYY-MM-DD)으로 LocalDate 변환
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        try {
            List<HomePageGameDto> games = homePageGameService.getGamesByDate(date);
            return ResponseEntity.ok(games);
        } catch (Exception ex) {
            log.warn("KBO schedule fallback applied - date={}, reason={}", date, ex.getMessage());
            return ResponseEntity.ok(List.of());
        }
    }

    // 특정 시즌의 팀 순위 조회
    @GetMapping("/rankings/{seasonYear}")
    public ResponseEntity<List<HomePageTeamRankingDto>> getTeamRankings(
            @PathVariable int seasonYear) {
        try {
            List<HomePageTeamRankingDto> rankings = homePageGameService.getTeamRankings(seasonYear);
            return ResponseEntity.ok(rankings);
        } catch (Exception ex) {
            log.warn("KBO rankings fallback applied - seasonYear={}, reason={}", seasonYear, ex.getMessage());
            return ResponseEntity.ok(List.of());
        }
    }

    // 각 리그별 시즌 시작 날짜를 조회
    @GetMapping("/league-start-dates")
    public ResponseEntity<LeagueStartDatesDto> getLeagueStartDates() {
        try {
            LeagueStartDatesDto startDates = homePageGameService.getLeagueStartDates();
            return ResponseEntity.ok(startDates);
        } catch (Exception ex) {
            LocalDate today = LocalDate.now();
            log.warn("KBO league start dates fallback applied - date={}, reason={}", today, ex.getMessage());
            return ResponseEntity.ok(
                    LeagueStartDatesDto.builder()
                            .regularSeasonStart(today.toString())
                            .postseasonStart(today.toString())
                            .koreanSeriesStart(today.toString())
                            .build());
        }
    }

    // 날짜 네비게이션 정보 조회
    @GetMapping("/schedule/navigation")
    public ResponseEntity<ScheduleNavigationDto> getScheduleNavigation(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        try {
            ScheduleNavigationDto navigation = homePageGameService.getScheduleNavigation(date);
            return ResponseEntity.ok(navigation);
        } catch (Exception ex) {
            log.warn("KBO schedule navigation fallback applied - date={}, reason={}", date, ex.getMessage());
            return ResponseEntity.ok(
                    ScheduleNavigationDto.builder()
                            .prevGameDate(null)
                            .nextGameDate(null)
                            .hasPrev(false)
                            .hasNext(false)
                            .build());
        }
    }

}
