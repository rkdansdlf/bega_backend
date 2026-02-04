package com.example.kbo.controller;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.kbo.dto.TeamSummaryDto;
import com.example.kbo.dto.TeamDiagnosticsDto;
import com.example.kbo.entity.TeamEntity;
import com.example.kbo.repository.TeamRepository;
import com.example.kbo.service.TeamDiagnosticsService;

import lombok.RequiredArgsConstructor;

/**
 * TeamController
 *
 * KBO 팀 조회 API (기본값: 활성 팀만 반환)
 */
@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
public class TeamController {

    private static final Logger log = LoggerFactory.getLogger(TeamController.class);

    private final TeamRepository teamRepository;
    private final TeamDiagnosticsService diagnosticsService;

    /**
     * 팀 목록 조회
     *
     * GET /api/teams?includeInactive=false
     *
     * @param includeInactive 비활성 팀 포함 여부
     * @return 팀 목록
     */
    @GetMapping
    public ResponseEntity<List<TeamSummaryDto>> getTeams(
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        log.info("GET /api/teams - includeInactive={}", includeInactive);

        List<TeamEntity> teams = includeInactive
                ? teamRepository.findAll()
                : teamRepository.findAllActiveTeams();
        return ResponseEntity.ok(teams.stream().map(TeamSummaryDto::from).toList());
    }

    /**
     * 활성 팀 목록 조회
     *
     * GET /api/teams/active
     *
     * @return 활성 팀 목록
     */
    @GetMapping("/active")
    public ResponseEntity<List<TeamSummaryDto>> getActiveTeams() {
        log.info("GET /api/teams/active");
        return ResponseEntity.ok(teamRepository.findAllActiveTeams().stream()
                .map(TeamSummaryDto::from)
                .toList());
    }

    /**
     * 팀 코드 정규화 진단
     *
     * GET /api/teams/diagnostics
     */
    @GetMapping("/diagnostics")
    public ResponseEntity<TeamDiagnosticsDto> getDiagnostics() {
        log.info("GET /api/teams/diagnostics");
        return ResponseEntity.ok(diagnosticsService.getDiagnostics());
    }
}
