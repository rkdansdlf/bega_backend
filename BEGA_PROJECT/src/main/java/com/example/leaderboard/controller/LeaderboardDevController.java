package com.example.leaderboard.controller;

import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.leaderboard.service.LeaderboardService;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Tag(name = "리더보드-개발", description = "리더보드 개발/테스트용 엔드포인트")
@RestController
@RequestMapping("/api/leaderboard")
@RequiredArgsConstructor
@Profile({ "dev", "local" })
@Slf4j
public class LeaderboardDevController {

    private final LeaderboardService leaderboardService;

    @PostMapping("/seed-test-data")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> seedTestData() {
        log.info("Seeding test data for leaderboard");
        int seededCount = leaderboardService.seedTestData();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "테스트 데이터가 생성되었습니다.",
                "seededCount", seededCount));
    }
}
