package com.example.demo.controller;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.example.demo.entity.TeamHistoryEntity;
import com.example.demo.service.TeamHistoryService;

import lombok.RequiredArgsConstructor;

/**
 * TeamHistoryController
 *
 * KBO 팀 역사 관련 REST API 엔드포인트
 * 1982-2024년 시즌별 팀 정보, 순위, 로고 등의 데이터 제공
 */
@RestController
@RequestMapping("/api/team-history")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class TeamHistoryController {

    private static final Logger log = LoggerFactory.getLogger(TeamHistoryController.class);

    private final TeamHistoryService historyService;

    /**
     * 데이터가 있는 모든 시즌 목록 조회
     *
     * GET /api/team-history/seasons
     *
     * @return 시즌 목록 (내림차순)
     */
    @GetMapping("/seasons")
    public ResponseEntity<List<Integer>> getAvailableSeasons() {
        log.info("GET /api/team-history/seasons - Fetching available seasons");

        List<Integer> seasons = historyService.getAvailableSeasons();
        return ResponseEntity.ok(seasons);
    }

    /**
     * 특정 시즌의 모든 팀 조회
     *
     * GET /api/team-history/season/{season}
     *
     * @param season 시즌 연도
     * @return 해당 시즌의 팀 목록
     */
    @GetMapping("/season/{season}")
    public ResponseEntity<List<TeamHistoryEntity>> getSeasonTeams(@PathVariable Integer season) {
        log.info("GET /api/team-history/season/{} - Fetching teams for season", season);

        // 시즌 유효성 검사
        if (season < 1982 || season > 2030) {
            return ResponseEntity.badRequest().build();
        }

        List<TeamHistoryEntity> teams = historyService.getHistoryBySeason(season);

        if (teams.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(teams);
    }

    /**
     * 특정 시즌의 팀 순위 조회
     *
     * GET /api/team-history/season/{season}/standings
     *
     * @param season 시즌 연도
     * @return 순위순으로 정렬된 팀 목록
     */
    @GetMapping("/season/{season}/standings")
    public ResponseEntity<List<TeamHistoryEntity>> getSeasonStandings(@PathVariable Integer season) {
        log.info("GET /api/team-history/season/{}/standings - Fetching standings", season);

        // 시즌 유효성 검사
        if (season < 1982 || season > 2030) {
            return ResponseEntity.badRequest().build();
        }

        List<TeamHistoryEntity> standings = historyService.getStandingsBySeason(season);

        if (standings.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(standings);
    }

    /**
     * 특정 팀의 특정 시즌 정보 조회
     *
     * GET /api/team-history/team/{teamCode}/season/{season}
     *
     * @param teamCode 팀 코드
     * @param season 시즌 연도
     * @return 팀-시즌 정보
     */
    @GetMapping("/team/{teamCode}/season/{season}")
    public ResponseEntity<TeamHistoryEntity> getTeamSeason(
            @PathVariable String teamCode,
            @PathVariable Integer season) {

        log.info("GET /api/team-history/team/{}/season/{} - Fetching team season info",
            teamCode, season);

        return historyService.getTeamInSeason(teamCode, season)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 특정 시즌의 통계 정보
     *
     * GET /api/team-history/statistics/{season}
     *
     * @param season 시즌 연도
     * @return 시즌 통계 (팀 수, 평균 순위 등)
     */
    @GetMapping("/statistics/{season}")
    public ResponseEntity<Map<String, Object>> getSeasonStatistics(@PathVariable Integer season) {
        log.info("GET /api/team-history/statistics/{} - Fetching season statistics", season);

        // 시즌 유효성 검사
        if (season < 1982 || season > 2030) {
            return ResponseEntity.badRequest().build();
        }

        Map<String, Object> statistics = historyService.getSeasonStatistics(season);

        // 데이터가 없으면 404
        if ((int) statistics.getOrDefault("totalTeams", 0) == 0) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(statistics);
    }

    /**
     * 특정 팀 코드의 전체 역사 조회
     *
     * GET /api/team-history/team/{teamCode}
     *
     * @param teamCode 팀 코드
     * @return 팀 코드의 모든 역사 (최신순)
     */
    @GetMapping("/team/{teamCode}")
    public ResponseEntity<List<TeamHistoryEntity>> getTeamCodeHistory(@PathVariable String teamCode) {
        log.info("GET /api/team-history/team/{} - Fetching team code history", teamCode);

        List<TeamHistoryEntity> history = historyService.getTeamCodeHistory(teamCode);

        if (history.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(history);
    }

    /**
     * 연도 범위로 역사 조회
     *
     * GET /api/team-history/range?start=2020&end=2024
     *
     * @param startYear 시작 연도
     * @param endYear 종료 연도
     * @return 해당 기간의 모든 팀 역사
     */
    @GetMapping("/range")
    public ResponseEntity<List<TeamHistoryEntity>> getHistoryByRange(
            @RequestParam Integer startYear,
            @RequestParam Integer endYear) {

        log.info("GET /api/team-history/range?start={}&end={} - Fetching history by range",
            startYear, endYear);

        // 유효성 검사
        if (startYear < 1982 || endYear > 2030 || startYear > endYear) {
            return ResponseEntity.badRequest().build();
        }

        List<TeamHistoryEntity> history = historyService.getHistoryByYearRange(startYear, endYear);
        return ResponseEntity.ok(history);
    }

    /**
     * 최근 시즌들의 데이터 조회
     *
     * GET /api/team-history/recent?limit=5
     *
     * @param limit 조회할 시즌 수 (기본값: 5)
     * @return 최근 N개 시즌의 모든 데이터
     */
    @GetMapping("/recent")
    public ResponseEntity<List<TeamHistoryEntity>> getRecentSeasons(
            @RequestParam(defaultValue = "5") int limit) {

        log.info("GET /api/team-history/recent?limit={} - Fetching recent seasons", limit);

        // 유효성 검사
        if (limit < 1 || limit > 50) {
            return ResponseEntity.badRequest().build();
        }

        List<TeamHistoryEntity> history = historyService.getRecentSeasons(limit);
        return ResponseEntity.ok(history);
    }

    /**
     * 구장별 팀 역사 조회
     *
     * GET /api/team-history/stadium/{stadium}
     *
     * @param stadium 구장명
     * @return 해당 구장을 사용한 팀들의 역사
     */
    @GetMapping("/stadium/{stadium}")
    public ResponseEntity<List<TeamHistoryEntity>> getHistoryByStadium(@PathVariable String stadium) {
        log.info("GET /api/team-history/stadium/{} - Fetching history by stadium", stadium);

        List<TeamHistoryEntity> history = historyService.getHistoryByStadium(stadium);

        if (history.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(history);
    }
}
