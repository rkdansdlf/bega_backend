package com.example.homePage;

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

	// 특정 날짜의 KBO 경기 목록을 조회
	@GetMapping("/schedule")
    public ResponseEntity<List<HomePageGameDto>> getGamesByDate(
        // @RequestParam으로 "date" 파라미터를 받고, ISO_DATE 형식(YYYY-MM-DD)으로 LocalDate 변환
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date 
    ) {
        List<HomePageGameDto> games = homePageGameService.getGamesByDate(date);
        return ResponseEntity.ok(games);
    }
	
	// 특정 시즌의 팀 순위 조회
	@GetMapping("/rankings/{seasonYear}")
    public ResponseEntity<List<HomePageTeamRankingDto>> getTeamRankings(
        @PathVariable int seasonYear
    ) {
        List<HomePageTeamRankingDto> rankings = homePageGameService.getTeamRankings(seasonYear);
        return ResponseEntity.ok(rankings);
    }
	// 
	@GetMapping("/league-start-dates")
	public ResponseEntity<LeagueStartDatesDto> getLeagueStartDates() {
	    LeagueStartDatesDto startDates = homePageGameService.getLeagueStartDates();
	    return ResponseEntity.ok(startDates);
	}
	
}
