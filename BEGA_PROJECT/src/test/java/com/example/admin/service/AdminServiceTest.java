package com.example.admin.service;

import com.example.admin.repository.AdminNonCanonicalCleanupTrackerRepository;
import com.example.admin.repository.AuditLogRepository;
import com.example.admin.exception.MateDeletionBlockedException;
import com.example.auth.entity.UserEntity;
import com.example.auth.repository.RefreshRepository;
import com.example.auth.repository.UserRepository;
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

import java.util.List;
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
    @Mock private AdminMateDeletionGuard mateDeletionGuard;

    @InjectMocks
    private AdminService adminService;

    @Test
    void deleteUserCommitsDisablementBeforeCleanupReadsAndPartyLocks() {
        UserEntity disabledUser = UserEntity.builder()
                .id(51L)
                .email("deleted@example.com")
                .role("ROLE_USER")
                .enabled(false)
                .tokenVersion(4)
                .build();
        given(deletionPreparationService.disableForDeletion(51L, null)).willReturn(disabledUser);
        given(userRepository.findById(51L)).willReturn(Optional.of(disabledUser));
        given(likeRepository.findByUser(disabledUser)).willReturn(List.of());
        given(commentRepository.findByAuthor(disabledUser)).willReturn(List.of());
        given(cheerPostRepository.findByAuthor(disabledUser)).willReturn(List.of());

        adminService.deleteUser(51L, null);

        InOrder order = inOrder(userRepository, deletionPreparationService, partyService);
        order.verify(userRepository).findById(51L);
        order.verify(deletionPreparationService).disableForDeletion(51L, null);
        order.verify(userRepository).findById(51L);
        order.verify(partyService).handleUserDeletion(51L);
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
