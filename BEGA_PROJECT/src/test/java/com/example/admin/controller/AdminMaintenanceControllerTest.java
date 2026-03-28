package com.example.admin.controller;

import com.example.cheerboard.scheduler.CheerStorageScheduler;
import com.example.common.dto.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminMaintenanceControllerTest {

    @Mock
    private CheerStorageScheduler cheerStorageScheduler;

    @InjectMocks
    private AdminMaintenanceController controller;

    @Test
    @DisplayName("소프트 삭제된 게시글 정리 작업을 실행한다")
    void cleanupSoftDeletedCheerPosts_callsSchedulerAndReturnsSuccess() {
        ResponseEntity<ApiResponse> result = controller.cleanupSoftDeletedCheerPosts();

        assertThat(result.getBody().isSuccess()).isTrue();
        verify(cheerStorageScheduler).cleanupDeletedPosts();
    }
}
