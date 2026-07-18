package com.example.admin.controller;

import com.example.common.dto.ApiResponse;
import com.example.prediction.RankingPredictionService;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/maintenance/ranking-predictions")
@PreAuthorize("hasRole('ADMIN')")
public class AdminRankingPredictionController {

    private final RankingPredictionService rankingPredictionService;

    @PostMapping("/settle")
    public ResponseEntity<ApiResponse<Integer>> settleSeason(@RequestParam int seasonYear) {
        int settledCount = rankingPredictionService.settleSeason(seasonYear);
        return ResponseEntity.ok(ApiResponse.success(
                seasonYear + " 시즌 순위 예측 정산을 실행했습니다.",
                settledCount));
    }
}
