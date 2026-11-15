package com.example.admin.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import com.example.admin.repository.AdminNonCanonicalCleanupTrackerRepository;
import com.example.admin.repository.AuditLogRepository;
import com.example.admin.exception.MateDeletionBlockedException;
import com.example.auth.repository.RefreshRepository;
import com.example.auth.repository.UserRepository;
import com.example.auth.service.AccountDeletionFinalizationService;
import com.example.cheerboard.repo.CheerCommentRepo;
import com.example.cheerboard.repo.CheerPostLikeRepo;
import com.example.cheerboard.repo.CheerPostRepo;
import com.example.cheerboard.repo.CheerReportRepo;
import com.example.mate.repository.PartyRepository;
import com.example.mate.entity.Party;
import com.example.mate.service.PartyService;
import com.example.prediction.PredictionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;

import java.util.Optional;

import static org.mockito.BDDMockito.given;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private CheerPostRepo cheerPostRepository;
    @Mock private CheerReportRepo cheerReportRepo;
    @Mock private PartyRepository partyRepository;
    @Mock private CheerCommentRepo commentRepository;
    @Mock private CheerPostLikeRepo likeRepository;
    @Mock private CacheManager cacheManager;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private AdminNonCanonicalCleanupTrackerRepository nonCanonicalCleanupTrackerRepository;
    @Mock private PartyService partyService;
    @Mock private RefreshRepository refreshRepository;
    @Mock private PredictionService predictionService;
    @Mock private AdminUserDeletionPreparationService deletionPreparationService;
    @Mock private AccountDeletionFinalizationService deletionFinalizationService;
    @Mock private AdminMateDeletionGuard mateDeletionGuard;

    @InjectMocks
    private AdminService adminService;

    @Test
    void deleteUserPreparesAccountBeforeFinalization() {
        adminService.deleteUser(51L, null);

        InOrder order = inOrder(deletionPreparationService, deletionFinalizationService);
        order.verify(deletionPreparationService).disableForDeletion(51L, null);
        order.verify(deletionFinalizationService).finalizeImmediatelyAfterAdminDeletion(51L, null);
    }

    @Test
    void getReportsAlwaysPassesNonNullDateBoundsBecausePostgresCannotTypeNullTimestamps() {
        given(cheerReportRepo.findForAdmin(any(), any(), any(), any(), any())).willReturn(Page.empty());
        ArgumentCaptor<LocalDateTime> from = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> to = ArgumentCaptor.forClass(LocalDateTime.class);

        adminService.getReports(null, null, null, null, 0, 20);
        adminService.getReports(null, null, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2), 0, 20);

        verify(cheerReportRepo, org.mockito.Mockito.times(2))
                .findForAdmin(isNull(), isNull(), from.capture(), to.capture(), any());
        assertThat(from.getAllValues()).doesNotContainNull();
        assertThat(to.getAllValues()).doesNotContainNull();
        // No filter: wide bounds that cover every row.
        assertThat(from.getAllValues().get(0)).isBefore(LocalDateTime.of(2000, 1, 1, 0, 0));
        assertThat(to.getAllValues().get(0)).isAfter(LocalDateTime.of(2100, 1, 1, 0, 0));
        // Explicit dates: whole first day through the last nanosecond of the last day.
        assertThat(from.getAllValues().get(1)).isEqualTo(LocalDateTime.of(2026, 10, 1, 0, 0));
        assertThat(to.getAllValues().get(1)).isEqualTo(LocalDateTime.of(2026, 10, 2, 23, 59, 59, 999_999_999));
    }

    @Test
    void deleteMateBlocksWhenGuardFindsLinkedHistory() {
        Party party = Party.builder().id(91L).description("기록이 있는 모임").hostId(7L).build();
        given(partyRepository.findByIdForUpdate(91L)).willReturn(Optional.of(party));
        doThrow(new MateDeletionBlockedException(91L)).when(mateDeletionGuard).ensureNoLinkedRecords(91L);

        assertThatThrownBy(() -> adminService.deleteMate(91L, 1L))
                .isInstanceOf(MateDeletionBlockedException.class);

        verify(partyRepository, never()).delete(party);
        verify(auditLogRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void deleteMateAllowsHardDeleteWhenNoLinkedHistoryExists() {
        Party party = Party.builder().id(92L).description("빈 모임").hostId(8L).build();
        given(partyRepository.findByIdForUpdate(92L)).willReturn(Optional.of(party));

        adminService.deleteMate(92L, null);

        verify(mateDeletionGuard).ensureNoLinkedRecords(92L);
        verify(partyRepository).delete(party);
    }
}
