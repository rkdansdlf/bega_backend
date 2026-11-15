package com.example.admin.controller;

import com.example.cheerboard.scheduler.CheerStorageScheduler;
import com.example.common.readonly.ReadOnlyVerificationPolicy;
import com.example.common.dto.ApiResponse;
import org.springframework.beans.factory.ObjectProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile({ "dev", "dev-adb" })
@RequiredArgsConstructor
@RequestMapping("/api/admin/maintenance")
public class AdminMaintenanceController {

    private final ObjectProvider<CheerStorageScheduler> cheerStorageSchedulerProvider;
    private final ReadOnlyVerificationPolicy readOnlyVerificationPolicy;

    @PostMapping("/cheer-posts/cleanup")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> cleanupSoftDeletedCheerPosts() {
        readOnlyVerificationPolicy.requireWritable();
        CheerStorageScheduler cheerStorageScheduler = cheerStorageSchedulerProvider.getIfAvailable();
        if (cheerStorageScheduler == null) {
            throw new IllegalStateException("Cheer storage cleanup is unavailable.");
        }
        cheerStorageScheduler.cleanupDeletedPosts();
        return ResponseEntity.ok(ApiResponse.success("Soft deleted 응원 게시글 정리 작업을 실행했습니다."));
    }
}
