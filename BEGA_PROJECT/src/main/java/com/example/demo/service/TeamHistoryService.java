package com.example.demo.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.entity.TeamHistoryEntity;
import com.example.demo.repo.TeamHistoryRepository;

import lombok.RequiredArgsConstructor;

/**
 * TeamHistoryService
 *
 * KBO 팀 역사 관련 비즈니스 로직을 처리하는 서비스
 * 1982-2024년 시즌별 팀 정보, 순위, 로고 등의 역사 데이터 제공
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeamHistoryService {

    private static final Logger log = LoggerFactory.getLogger(TeamHistoryService.class);
    private static final int CURRENT_YEAR = 2024;

    private final TeamHistoryRepository historyRepository;

    /**
     * 특정 시즌의 모든 팀 조회
     *
     * @param season 시즌 연도
     * @return 해당 시즌의 팀 목록
     */
    public List<TeamHistoryEntity> getHistoryBySeason(Integer season) {
        log.debug("Fetching team history for season: {}", season);
        return historyRepository.findBySeason(season);
    }

    /**
     * 특정 시즌의 팀 순위 조회
     *
     * @param season 시즌 연도
     * @return 순위순으로 정렬된 팀 목록
     */
    public List<TeamHistoryEntity> getStandingsBySeason(Integer season) {
        log.debug("Fetching standings for season: {}", season);
        return historyRepository.findBySeasonOrderByRanking(season);
    }

    /**
     * 프랜차이즈의 전체 역사 조회
     *
     * @param franchiseId 프랜차이즈 ID
     * @return 프랜차이즈의 모든 역사 레코드 (최신순)
     */
    public List<TeamHistoryEntity> getFranchiseHistory(Integer franchiseId) {
        log.debug("Fetching complete history for franchise id: {}", franchiseId);
        return historyRepository.findByFranchiseIdOrderBySeasonDesc(franchiseId);
    }

    /**
     * 프랜차이즈의 최근 N년 역사 조회
     *
     * @param franchiseId 프랜차이즈 ID
     * @param years 조회할 연도 수
     * @return 최근 N년의 역사 레코드
     */
    public List<TeamHistoryEntity> getRecentHistory(Integer franchiseId, int years) {
        log.debug("Fetching recent {} years history for franchise id: {}", years, franchiseId);

        int startSeason = CURRENT_YEAR - years + 1;
        return historyRepository.findByFranchiseIdAndSeasonGreaterThanEqualOrderBySeasonDesc(
            franchiseId, startSeason
        );
    }

    /**
     * 특정 팀의 특정 시즌 정보 조회
     *
     * @param teamCode 팀 코드
     * @param season 시즌 연도
     * @return 팀-시즌 정보
     */
    public Optional<TeamHistoryEntity> getTeamInSeason(String teamCode, Integer season) {
        log.debug("Fetching team {} in season {}", teamCode, season);
        return historyRepository.findBySeasonAndTeamCode(season, teamCode);
    }

    /**
     * 데이터가 있는 모든 시즌 목록 조회
     *
     * @return 시즌 목록 (내림차순)
     */
    public List<Integer> getAvailableSeasons() {
        log.debug("Fetching all available seasons");
        return historyRepository.findAll()
            .stream()
            .map(TeamHistoryEntity::getSeason)
            .distinct()
            .sorted((a, b) -> b.compareTo(a))
            .collect(Collectors.toList());
    }

    /**
     * 특정 시즌의 통계 정보
     *
     * @param season 시즌 연도
     * @return 시즌 통계 (팀 수, 평균 순위 등)
     */
    public Map<String, Object> getSeasonStatistics(Integer season) {
        log.debug("Calculating statistics for season: {}", season);

        List<TeamHistoryEntity> teams = historyRepository.findBySeason(season);

        Map<String, Object> stats = new HashMap<>();
        stats.put("season", season);
        stats.put("totalTeams", teams.size());

        // 순위 통계 계산
        long teamsWithRanking = teams.stream()
            .filter(t -> t.getRanking() != null)
            .count();

        if (teamsWithRanking > 0) {
            double avgRanking = teams.stream()
                .filter(t -> t.getRanking() != null)
                .mapToInt(TeamHistoryEntity::getRanking)
                .average()
                .orElse(0.0);

            stats.put("teamsWithRanking", teamsWithRanking);
            stats.put("averageRanking", avgRanking);
        }

        // 사용된 구장 목록
        List<String> stadiums = teams.stream()
            .map(TeamHistoryEntity::getStadium)
            .filter(s -> s != null && !s.isEmpty())
            .distinct()
            .collect(Collectors.toList());

        stats.put("stadiums", stadiums);
        stats.put("stadiumCount", stadiums.size());

        return stats;
    }

    /**
     * 특정 팀 코드의 역사 조회
     *
     * @param teamCode 팀 코드
     * @return 팀 코드의 모든 역사 (최신순)
     */
    public List<TeamHistoryEntity> getTeamCodeHistory(String teamCode) {
        log.debug("Fetching history for team code: {}", teamCode);
        return historyRepository.findByTeamCodeOrderBySeasonDesc(teamCode);
    }

    /**
     * 연도 범위로 역사 조회
     *
     * @param startYear 시작 연도
     * @param endYear 종료 연도
     * @return 해당 기간의 모든 팀 역사
     */
    public List<TeamHistoryEntity> getHistoryByYearRange(Integer startYear, Integer endYear) {
        log.debug("Fetching history between {} and {}", startYear, endYear);
        return historyRepository.findBySeasonBetween(startYear, endYear);
    }

    /**
     * 최근 시즌들의 데이터 조회
     *
     * @param limit 조회할 시즌 수
     * @return 최근 N개 시즌의 모든 데이터
     */
    public List<TeamHistoryEntity> getRecentSeasons(int limit) {
        log.debug("Fetching recent {} seasons data", limit);
        return historyRepository.findRecentSeasons(limit);
    }

    /**
     * 구장별 팀 역사 조회
     *
     * @param stadium 구장명
     * @return 해당 구장을 사용한 팀들의 역사
     */
    public List<TeamHistoryEntity> getHistoryByStadium(String stadium) {
        log.debug("Fetching history for stadium: {}", stadium);
        return historyRepository.findByStadium(stadium);
    }

    /**
     * 프랜차이즈의 특정 시즌 정보 조회
     *
     * @param franchiseId 프랜차이즈 ID
     * @param season 시즌 연도
     * @return 프랜차이즈의 해당 시즌 정보
     */
    public Optional<TeamHistoryEntity> getFranchiseSeasonInfo(Integer franchiseId, Integer season) {
        log.debug("Fetching franchise {} info for season {}", franchiseId, season);
        return historyRepository.findByFranchiseIdAndSeason(franchiseId, season);
    }
}
