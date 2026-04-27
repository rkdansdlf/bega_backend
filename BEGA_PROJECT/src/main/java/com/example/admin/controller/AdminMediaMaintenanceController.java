package com.example.admin.controller;

import com.example.common.dto.ApiResponse;
import com.example.media.entity.MediaCleanupTarget;
import com.example.media.entity.MediaDomain;
import com.example.media.service.MediaMaintenanceService;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/maintenance/media")
@PreAuthorize("hasRole('ADMIN')")
public class AdminMediaMaintenanceController {

    private final MediaMaintenanceService mediaMaintenanceService;

    @PostMapping("/smoke")
    public ResponseEntity<ApiResponse> runSmoke(
            @RequestParam(defaultValue = "10") int sampleLimit,
            @RequestParam(required = false) String domains) {
        return ResponseEntity.ok(ApiResponse.success(
                "공통 미디어 smoke 검증을 실행했습니다.",
                mediaMaintenanceService.runSmoke(sampleLimit, parseDomains(domains))));
    }

    @PostMapping("/backfill")
    public ResponseEntity<ApiResponse> backfillExistingData(
            @RequestParam(defaultValue = "false") boolean apply,
            @RequestParam(defaultValue = "200") int batchSize,
            @RequestParam(required = false) String domains,
            @RequestParam(defaultValue = "false") boolean clearBrokenChatImages) {
        return ResponseEntity.ok(ApiResponse.success(
                apply ? "공통 미디어 백필을 적용했습니다." : "공통 미디어 백필 dry-run을 실행했습니다.",
                mediaMaintenanceService.backfillExistingData(
                        apply,
                        batchSize,
                        parseDomains(domains),
                        clearBrokenChatImages)));
    }

    @PostMapping("/cleanup")
    public ResponseEntity<ApiResponse> runCleanup(
            @RequestParam(required = false) String targets) {
        return ResponseEntity.ok(ApiResponse.success(
                "공통 미디어 cleanup을 실행했습니다.",
                mediaMaintenanceService.runCleanup(parseCleanupTargets(targets))));
    }

    private List<MediaDomain> parseDomains(String domains) {
        if (domains == null || domains.isBlank()) {
            return List.of();
        }

        return Arrays.stream(domains.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> MediaDomain.valueOf(value.toUpperCase(Locale.ROOT)))
                .toList();
    }

    private List<MediaCleanupTarget> parseCleanupTargets(String targets) {
        if (targets == null || targets.isBlank()) {
            return List.of();
        }

        return Arrays.stream(targets.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> MediaCleanupTarget.valueOf(value.toUpperCase(Locale.ROOT)))
                .toList();
    }
}
