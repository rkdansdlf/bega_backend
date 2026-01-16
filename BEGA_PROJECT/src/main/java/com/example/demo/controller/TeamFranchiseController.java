package com.example.demo.controller;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.example.demo.entity.TeamEntity;
import com.example.demo.entity.TeamFranchiseEntity;
import com.example.demo.entity.TeamHistoryEntity;
import com.example.demo.service.TeamFranchiseService;
import com.example.demo.service.TeamHistoryService;

import lombok.RequiredArgsConstructor;

/**
 * TeamFranchiseController
 *
 * KBO 프랜차이즈 관련 REST API 엔드포인트
 */
@RestController
@RequestMapping("/api/franchises")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class TeamFranchiseController {

    private static final Logger log = LoggerFactory.getLogger(TeamFranchiseController.class);

    private final TeamFranchiseService franchiseService;
    private final TeamHistoryService historyService;

    /**
     * 모든 KBO 프랜차이즈 조회
     *
     * GET /api/franchises
     *
     * @return 10개 프랜차이즈 목록
     */
    @GetMapping
    public ResponseEntity<List<TeamFranchiseEntity>> getAllFranchises() {
        log.info("GET /api/franchises - Fetching all franchises");

        List<TeamFranchiseEntity> franchises = franchiseService.getAllFranchises();
        return ResponseEntity.ok(franchises);
    }

    /**
     * ID로 프랜차이즈 조회
     *
     * GET /api/franchises/{id}
     *
     * @param id 프랜차이즈 ID
     * @return 프랜차이즈 정보
     */
    @GetMapping("/{id}")
    public ResponseEntity<TeamFranchiseEntity> getFranchiseById(@PathVariable Integer id) {
        log.info("GET /api/franchises/{} - Fetching franchise by id", id);

        return franchiseService.getFranchiseById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 코드로 프랜차이즈 조회
     *
     * GET /api/franchises/code/{code}
     *
     * @param code 팀 코드 (예: SS, OB, KIA)
     * @return 프랜차이즈 정보
     */
    @GetMapping("/code/{code}")
    public ResponseEntity<TeamFranchiseEntity> getFranchiseByCode(@PathVariable String code) {
        log.info("GET /api/franchises/code/{} - Fetching franchise by code", code);

        return franchiseService.getFranchiseByCode(code)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 프랜차이즈의 모든 팀 조회 (역사적 팀 포함)
     *
     * GET /api/franchises/{id}/teams
     *
     * @param id 프랜차이즈 ID
     * @return 팀 목록
     */
    @GetMapping("/{id}/teams")
    public ResponseEntity<List<TeamEntity>> getFranchiseTeams(@PathVariable Integer id) {
        log.info("GET /api/franchises/{}/teams - Fetching all teams for franchise", id);

        // 프랜차이즈 존재 여부 확인
        if (franchiseService.getFranchiseById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        List<TeamEntity> teams = franchiseService.getTeamsByFranchiseId(id);
        return ResponseEntity.ok(teams);
    }

    /**
     * 프랜차이즈의 현재 활성 팀 조회
     *
     * GET /api/franchises/{id}/current-team
     *
     * @param id 프랜차이즈 ID
     * @return 현재 활성 팀
     */
    @GetMapping("/{id}/current-team")
    public ResponseEntity<TeamEntity> getCurrentTeam(@PathVariable Integer id) {
        log.info("GET /api/franchises/{}/current-team - Fetching current team", id);

        return franchiseService.getCurrentTeamByFranchiseId(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 프랜차이즈 히스토리 조회
     *
     * GET /api/franchises/{id}/history
     *
     * @param id 프랜차이즈 ID
     * @return 프랜차이즈 전체 역사
     */
    @GetMapping("/{id}/history")
    public ResponseEntity<List<TeamHistoryEntity>> getFranchiseHistory(@PathVariable Integer id) {
        log.info("GET /api/franchises/{}/history - Fetching franchise history", id);

        // 프랜차이즈 존재 여부 확인
        if (franchiseService.getFranchiseById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        List<TeamHistoryEntity> history = historyService.getFranchiseHistory(id);
        return ResponseEntity.ok(history);
    }

    /**
     * 프랜차이즈 최근 히스토리 조회
     *
     * GET /api/franchises/{id}/history/recent?years=5
     *
     * @param id 프랜차이즈 ID
     * @param years 조회할 연도 수 (기본값: 5)
     * @return 최근 N년 히스토리
     */
    @GetMapping("/{id}/history/recent")
    public ResponseEntity<List<TeamHistoryEntity>> getRecentHistory(
            @PathVariable Integer id,
            @RequestParam(defaultValue = "5") int years) {

        log.info("GET /api/franchises/{}/history/recent?years={} - Fetching recent history", id, years);

        // 프랜차이즈 존재 여부 확인
        if (franchiseService.getFranchiseById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        // years 유효성 검사
        if (years < 1 || years > 50) {
            return ResponseEntity.badRequest().build();
        }

        List<TeamHistoryEntity> history = historyService.getRecentHistory(id, years);
        return ResponseEntity.ok(history);
    }

    /**
     * 프랜차이즈 메타데이터 조회
     *
     * GET /api/franchises/{id}/metadata
     *
     * @param id 프랜차이즈 ID
     * @return 메타데이터 (owner, ceo, address, homepage 등)
     */
    @GetMapping("/{id}/metadata")
    public ResponseEntity<Map<String, Object>> getFranchiseMetadata(@PathVariable Integer id) {
        log.info("GET /api/franchises/{}/metadata - Fetching franchise metadata", id);

        // 프랜차이즈 존재 여부 확인
        if (franchiseService.getFranchiseById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> metadata = franchiseService.getFranchiseMetadata(id);
        return ResponseEntity.ok(metadata);
    }

    /**
     * 프랜차이즈 이름 검색
     *
     * GET /api/franchises/search?keyword=삼성
     *
     * @param keyword 검색 키워드
     * @return 매칭되는 프랜차이즈 목록
     */
    @GetMapping("/search")
    public ResponseEntity<List<TeamFranchiseEntity>> searchFranchises(
            @RequestParam String keyword) {

        log.info("GET /api/franchises/search?keyword={} - Searching franchises", keyword);

        if (keyword == null || keyword.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        List<TeamFranchiseEntity> franchises = franchiseService.searchFranchisesByName(keyword);
        return ResponseEntity.ok(franchises);
    }
}
