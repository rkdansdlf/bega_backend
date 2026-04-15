package com.example.admin.controller;

import com.example.admin.dto.*;
import com.example.admin.service.AdminService;
import com.example.BegaDiary.Service.SeatViewService;
import com.example.common.dto.ApiResponse;
import com.example.prediction.GameInningScoreRequestDto;
import com.example.prediction.GameStatusMismatchDto;
import com.example.prediction.GameScoreSyncBatchResultDto;
import com.example.prediction.GameScoreSyncResultDto;
import com.example.prediction.GameStatusMismatchBatchResultDto;
import com.example.prediction.GameStatusRepairBatchResultDto;
import com.example.prediction.NonCanonicalGameDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

    @Mock
    private AdminService adminService;

    @Mock
    private SeatViewService seatViewService;

    @InjectMocks
    private AdminController controller;

    // ── getStats ──

    @Test
    @DisplayName("대시보드 통계를 조회한다")
    void getStats_returnsSuccess() {
        AdminStatsDto stats = mock(AdminStatsDto.class);
        when(adminService.getStats()).thenReturn(stats);

        ResponseEntity<ApiResponse> result = controller.getStats();

        assertThat(result.getBody().isSuccess()).isTrue();
        assertThat(result.getBody().getData()).isEqualTo(stats);
    }

    // ── getUsers ──

    @Test
    @DisplayName("검색어가 있으면 검색어로 유저 목록을 조회한다")
    void getUsers_withSearch_callsServiceWithSearch() {
        List<AdminUserDto> users = List.of(mock(AdminUserDto.class));
        when(adminService.getUsers("test")).thenReturn(users);

        ResponseEntity<ApiResponse> result = controller.getUsers("test");

        assertThat(result.getBody().isSuccess()).isTrue();
        verify(adminService).getUsers("test");
    }

    @Test
    @DisplayName("검색어가 없으면 null로 조회한다")
    void getUsers_withoutSearch_callsServiceWithNull() {
        when(adminService.getUsers(null)).thenReturn(List.of());

        controller.getUsers(null);

        verify(adminService).getUsers(null);
    }

    // ── getPosts ──

    @Test
    @DisplayName("게시글 목록을 조회한다")
    void getPosts_returnsSuccess() {
        when(adminService.getPosts()).thenReturn(List.of());

        ResponseEntity<ApiResponse> result = controller.getPosts();

        assertThat(result.getBody().isSuccess()).isTrue();
    }

    // ── getReports ──

    @Test
    @DisplayName("필터로 신고 목록을 조회한다")
    void getReports_withFilters_callsServiceCorrectly() {
        Page<AdminReportDto> page = new PageImpl<>(List.of());
        when(adminService.getReports("PENDING", "SPAM", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 1), 0, 20))
                .thenReturn(page);

        ResponseEntity<ApiResponse> result = controller.getReports("PENDING", "SPAM",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 1), 0, 20);

        assertThat(result.getBody().isSuccess()).isTrue();
    }

    // ── getReport ──

    @Test
    @DisplayName("신고 상세를 조회한다")
    void getReport_returnsSuccess() {
        AdminReportDto dto = mock(AdminReportDto.class);
        when(adminService.getReport(1L)).thenReturn(dto);

        ResponseEntity<ApiResponse> result = controller.getReport(1L);

        assertThat(result.getBody().getData()).isEqualTo(dto);
    }

    // ── getSeatViews ──

    @Test
    @DisplayName("시야뷰 후보 목록을 필터로 조회한다")
    void getSeatViews_withFilters_callsServiceCorrectly() {
        when(seatViewService.getAdminSeatViews("PENDING", "JAMSIL", "good", null, true))
                .thenReturn(List.of());

        controller.getSeatViews("PENDING", "JAMSIL", "good", null, true);

        verify(seatViewService).getAdminSeatViews("PENDING", "JAMSIL", "good", null, true);
    }

    // ── getSeatView ──

    @Test
    @DisplayName("시야뷰 후보 상세를 조회한다")
    void getSeatView_returnsSuccess() {
        AdminSeatViewDto dto = mock(AdminSeatViewDto.class);
        when(seatViewService.getAdminSeatView(1L)).thenReturn(dto);

        ResponseEntity<ApiResponse> result = controller.getSeatView(1L);

        assertThat(result.getBody().getData()).isEqualTo(dto);
    }

    // ── handleSeatView ──

    @Test
    @DisplayName("시야뷰를 처리한다")
    void handleSeatView_returnsSuccess() {
        AdminSeatViewActionReq req = mock(AdminSeatViewActionReq.class);
        AdminSeatViewDto dto = mock(AdminSeatViewDto.class);
        when(seatViewService.reviewSeatView(1L, 42L, req)).thenReturn(dto);

        ResponseEntity<ApiResponse> result = controller.handleSeatView(42L, 1L, req);

        assertThat(result.getBody().getData()).isEqualTo(dto);
    }

    // ── handleReport ──

    @Test
    @DisplayName("신고를 처리한다")
    void handleReport_returnsSuccess() {
        AdminReportActionReq req = mock(AdminReportActionReq.class);
        AdminReportDto dto = mock(AdminReportDto.class);
        when(adminService.handleReport(1L, req, 42L)).thenReturn(dto);

        ResponseEntity<ApiResponse> result = controller.handleReport(42L, 1L, req);

        assertThat(result.getBody().getData()).isEqualTo(dto);
    }

    // ── requestAppeal ──

    @Test
    @DisplayName("이의제기를 등록한다")
    void requestAppeal_returnsSuccess() {
        AdminReportAppealReq req = mock(AdminReportAppealReq.class);
        AdminReportDto dto = mock(AdminReportDto.class);
        when(adminService.requestAppeal(1L, req, 42L)).thenReturn(dto);

        ResponseEntity<ApiResponse> result = controller.requestAppeal(42L, 1L, req);

        assertThat(result.getBody().getData()).isEqualTo(dto);
    }

    // ── getMates ──

    @Test
    @DisplayName("메이트 목록을 조회한다")
    void getMates_returnsSuccess() {
        when(adminService.getMates()).thenReturn(List.of());

        ResponseEntity<ApiResponse> result = controller.getMates();

        assertThat(result.getBody().isSuccess()).isTrue();
    }

    // ── deleteUser ──

    @Test
    @DisplayName("유저를 삭제한다")
    void deleteUser_returnsSuccess() {
        controller.deleteUser(42L, 1L);

        verify(adminService).deleteUser(1L, 42L);
    }

    // ── deletePost ──

    @Test
    @DisplayName("게시글을 삭제한다")
    void deletePost_returnsSuccess() {
        controller.deletePost(42L, 1L);

        verify(adminService).deletePost(1L, 42L);
    }

    // ── deleteMate ──

    @Test
    @DisplayName("메이트를 삭제한다")
    void deleteMate_returnsSuccess() {
        controller.deleteMate(42L, 1L);

        verify(adminService).deleteMate(1L, 42L);
    }

    // ── getCacheStats ──

    @Test
    @DisplayName("캐시 통계를 조회한다")
    void getCacheStats_returnsSuccess() {
        Map<String, Object> stats = Map.of("hitRate", 0.95);
        when(adminService.getCacheStats()).thenReturn(stats);

        ResponseEntity<ApiResponse> result = controller.getCacheStats();

        assertThat(result.getBody().getData()).isEqualTo(stats);
    }

    // ── upsertInningScores ──

    @Test
    @DisplayName("이닝 스코어를 저장한다")
    void upsertInningScores_returnsSuccess() {
        List<GameInningScoreRequestDto> scores = List.of(mock(GameInningScoreRequestDto.class));
        when(adminService.upsertInningScores("GAME001", scores)).thenReturn(9);

        ResponseEntity<ApiResponse> result = controller.upsertInningScores("GAME001", scores);

        assertThat(result.getBody().isSuccess()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.getBody().getData();
        assertThat(data).containsEntry("gameId", "GAME001");
        assertThat(data).containsEntry("saved", 9);
    }

    @Test
    @DisplayName("경기 스냅샷을 동기화한다")
    void syncGameSnapshot_returnsSuccess() {
        GameScoreSyncResultDto resultDto = new GameScoreSyncResultDto(
                "GAME001",
                4,
                2,
                "COMPLETED",
                8,
                true,
                true,
                "LG",
                4
        );
        when(adminService.syncGameSnapshot("GAME001")).thenReturn(resultDto);

        ResponseEntity<ApiResponse> result = controller.syncGameSnapshot("GAME001");

        assertThat(result.getBody().isSuccess()).isTrue();
        assertThat(result.getBody().getData()).isEqualTo(resultDto);
    }

    @Test
    @DisplayName("날짜 범위 기준으로 경기 스냅샷을 일괄 동기화한다")
    void syncGameSnapshotsByDateRange_returnsSuccess() {
        GameScoreSyncBatchResultDto resultDto = new GameScoreSyncBatchResultDto(
                LocalDate.of(2026, 3, 29),
                LocalDate.of(2026, 3, 29),
                2,
                2,
                0,
                List.of()
        );
        when(adminService.syncGameSnapshotsByDateRange(LocalDate.of(2026, 3, 29), LocalDate.of(2026, 3, 29)))
                .thenReturn(resultDto);

        ResponseEntity<ApiResponse> result = controller.syncGameSnapshotsByDateRange(
                LocalDate.of(2026, 3, 29),
                null
        );

        assertThat(result.getBody().isSuccess()).isTrue();
        assertThat(result.getBody().getData()).isEqualTo(resultDto);
    }

    @Test
    @DisplayName("날짜 범위 기준으로 경기 상태 불일치를 조회한다")
    void getGameStatusMismatches_returnsSuccess() {
        GameStatusMismatchBatchResultDto resultDto = new GameStatusMismatchBatchResultDto(
                LocalDate.of(2026, 3, 29),
                LocalDate.of(2026, 3, 29),
                5,
                2,
                List.<GameStatusMismatchDto>of(),
                1,
                List.<NonCanonicalGameDto>of()
        );
        when(adminService.findGameStatusMismatchesByDateRange(LocalDate.of(2026, 3, 29), LocalDate.of(2026, 3, 29)))
                .thenReturn(resultDto);

        ResponseEntity<ApiResponse> result = controller.getGameStatusMismatches(
                LocalDate.of(2026, 3, 29),
                null
        );

        assertThat(result.getBody().isSuccess()).isTrue();
        assertThat(result.getBody().getData()).isEqualTo(resultDto);
    }

    @Test
    @DisplayName("날짜 범위 기준으로 경기 상태 불일치를 dry-run 복구한다")
    void repairGameStatusMismatches_returnsSuccess() {
        GameStatusRepairBatchResultDto resultDto = new GameStatusRepairBatchResultDto(
                LocalDate.of(2026, 3, 29),
                LocalDate.of(2026, 3, 29),
                true,
                5,
                2,
                0,
                List.<GameStatusMismatchDto>of(),
                List.<GameScoreSyncResultDto>of(),
                1,
                List.<NonCanonicalGameDto>of()
        );
        when(adminService.repairGameStatusMismatchesByDateRange(LocalDate.of(2026, 3, 29), LocalDate.of(2026, 3, 29), true))
                .thenReturn(resultDto);

        ResponseEntity<ApiResponse> result = controller.repairGameStatusMismatches(
                LocalDate.of(2026, 3, 29),
                null,
                true
        );

        assertThat(result.getBody().isSuccess()).isTrue();
        assertThat(result.getBody().getData()).isEqualTo(resultDto);
    }

    @Test
    @DisplayName("비정상 팀 코드 정제 tracker 목록을 조회한다")
    void getNonCanonicalCleanupTrackers_returnsSuccess() {
        List<AdminNonCanonicalCleanupTrackerDto> resultDto = List.of(
                new AdminNonCanonicalCleanupTrackerDto(
                        LocalDate.of(2026, 4, 14),
                        LocalDate.of(2026, 4, 14),
                        "https://tickets.example.com/noncanonical-20260414",
                        "ops-team",
                        "requested",
                        "raw team code cleanup requested",
                        java.time.LocalDateTime.of(2026, 4, 15, 10, 0),
                        List.of("20260414롯데00LG0")
                )
        );
        when(adminService.getNonCanonicalCleanupTrackers()).thenReturn(resultDto);

        ResponseEntity<ApiResponse> result = controller.getNonCanonicalCleanupTrackers();

        assertThat(result.getBody().isSuccess()).isTrue();
        assertThat(result.getBody().getData()).isEqualTo(resultDto);
    }

    @Test
    @DisplayName("비정상 팀 코드 정제 tracker를 저장한다")
    void upsertNonCanonicalCleanupTracker_returnsSuccess() {
        AdminNonCanonicalCleanupTrackerUpsertRequest request = new AdminNonCanonicalCleanupTrackerUpsertRequest(
                "https://tickets.example.com/noncanonical-20260414",
                "ops-team",
                "requested",
                "raw team code cleanup requested",
                List.of("20260414롯데00LG0")
        );
        AdminNonCanonicalCleanupTrackerDto resultDto = new AdminNonCanonicalCleanupTrackerDto(
                LocalDate.of(2026, 4, 14),
                LocalDate.of(2026, 4, 14),
                "https://tickets.example.com/noncanonical-20260414",
                "ops-team",
                "requested",
                "raw team code cleanup requested",
                java.time.LocalDateTime.of(2026, 4, 15, 10, 0),
                List.of("20260414롯데00LG0")
        );
        when(adminService.upsertNonCanonicalCleanupTracker(
                LocalDate.of(2026, 4, 14),
                LocalDate.of(2026, 4, 14),
                request,
                42L
        )).thenReturn(resultDto);

        ResponseEntity<ApiResponse> result = controller.upsertNonCanonicalCleanupTracker(
                42L,
                LocalDate.of(2026, 4, 14),
                LocalDate.of(2026, 4, 14),
                request
        );

        assertThat(result.getBody().isSuccess()).isTrue();
        assertThat(result.getBody().getData()).isEqualTo(resultDto);
    }

    @Test
    @DisplayName("비정상 팀 코드 정제 tracker를 삭제한다")
    void deleteNonCanonicalCleanupTracker_returnsNoContent() {
        ResponseEntity<Void> result = controller.deleteNonCanonicalCleanupTracker(
                LocalDate.of(2026, 4, 14),
                LocalDate.of(2026, 4, 14)
        );

        assertThat(result.getStatusCode().value()).isEqualTo(204);
        verify(adminService).deleteNonCanonicalCleanupTracker(
                LocalDate.of(2026, 4, 14),
                LocalDate.of(2026, 4, 14)
        );
    }
}
