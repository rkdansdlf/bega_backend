package com.example.homepage;

import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/home")
@RequiredArgsConstructor
public class HomeController {

    private final HomePageFacadeService homePageFacadeService;

    @GetMapping("/bootstrap")
    public ResponseEntity<HomeBootstrapResponseDto> getBootstrap(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate selectedDate = date == null ? LocalDate.now() : date;
        try {
            return ResponseEntity.ok(homePageFacadeService.getBootstrap(selectedDate));
        } catch (Exception e) {
            log.warn("Bootstrap failed for date={}, returning empty fallback: {}", selectedDate, e.getMessage());
            return ResponseEntity.ok(buildEmptyBootstrap(selectedDate));
        }
    }

    @GetMapping("/widgets")
    public ResponseEntity<HomeWidgetsResponseDto> getWidgets(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate selectedDate = date == null ? LocalDate.now() : date;
        try {
            return ResponseEntity.ok(homePageFacadeService.getWidgets(selectedDate));
        } catch (Exception e) {
            log.warn("Widgets failed for date={}, returning empty fallback: {}", selectedDate, e.getMessage());
            return ResponseEntity.ok(buildEmptyWidgets());
        }
    }

    private HomeBootstrapResponseDto buildEmptyBootstrap(LocalDate selectedDate) {
        return HomeBootstrapResponseDto.builder()
                .selectedDate(selectedDate.toString())
                .leagueStartDates(LeagueStartDatesDto.builder()
                        .regularSeasonStart(selectedDate.toString())
                        .postseasonStart(selectedDate.toString())
                        .koreanSeriesStart(selectedDate.toString())
                        .build())
                .navigation(HomeScheduleNavigationDto.builder()
                        .hasPrev(false)
                        .hasNext(false)
                        .build())
                .games(List.of())
                .scheduledGamesWindow(List.of())
                .rankingSeasonYear(selectedDate.getYear())
                .rankingSourceMessage("데이터를 불러오지 못했습니다.")
                .isOffSeason(false)
                .rankings(List.of())
                .build();
    }

    private HomeWidgetsResponseDto buildEmptyWidgets() {
        return HomeWidgetsResponseDto.builder()
                .hotCheerPosts(List.of())
                .featuredMates(List.of())
                .build();
    }
}
