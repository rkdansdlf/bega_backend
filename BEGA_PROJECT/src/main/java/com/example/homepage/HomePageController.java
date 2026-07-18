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

@RestController
@RequestMapping("/api/kbo")
@RequiredArgsConstructor
public class HomePageController {

    private final HomePageGameService homePageGameService;
    private final HomePageFacadeService homePageFacadeService;

    // 특정 날짜의 KBO 경기 목록을 조회
    @GetMapping("/schedule")
    public ResponseEntity<List<HomePageGameDto>> getGamesByDate(
            // @RequestParam으로 "date" 파라미터를 받고, ISO_DATE 형식(YYYY-MM-DD)으로 LocalDate 변환
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(homePageGameService.getGamesByDate(date));
    }

    // 특정 시즌의 팀 순위 조회
    @GetMapping("/rankings/{seasonYear:\\d+}")
    public ResponseEntity<List<HomePageTeamRankingDto>> getTeamRankings(
            @PathVariable int seasonYear) {
        return ResponseEntity.ok(homePageGameService.getTeamRankings(seasonYear));
    }

    @GetMapping("/rankings/snapshot")
    public ResponseEntity<HomeRankingSnapshotDto> getRankingSnapshot(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Integer seasonYear) {
        LocalDate selectedDate = date == null ? LocalDate.now() : date;
        return ResponseEntity.ok(homePageFacadeService.getRankingSnapshot(selectedDate, seasonYear));
    }

    // 각 리그별 시즌 시작 날짜를 조회
    @GetMapping("/league-start-dates")
    public ResponseEntity<LeagueStartDatesDto> getLeagueStartDates() {
        return ResponseEntity.ok(homePageGameService.getLeagueStartDates());
    }

    // 날짜 네비게이션 정보 조회
    @GetMapping("/schedule/navigation")
    public ResponseEntity<ScheduleNavigationDto> getScheduleNavigation(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(homePageGameService.getScheduleNavigation(date));
    }
}
