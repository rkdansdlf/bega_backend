package com.example.admin.service;

import com.example.admin.repository.AdminNonCanonicalCleanupTrackerRepository;
import com.example.admin.repository.AuditLogRepository;
import com.example.auth.entity.UserEntity;
import com.example.auth.repository.RefreshRepository;
import com.example.auth.repository.UserRepository;
import com.example.cheerboard.repo.CheerCommentRepo;
import com.example.cheerboard.repo.CheerPostLikeRepo;
import com.example.cheerboard.repo.CheerPostRepo;
import com.example.cheerboard.repo.CheerReportRepo;
import com.example.mate.repository.PartyRepository;
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
import static org.mockito.Mockito.inOrder;

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

    @InjectMocks
    private AdminService adminService;

    @Test
    void deleteUserCommitsDisablementBeforeCleanupReadsAndPartyLocks() {
        UserEntity disabledUser = UserEntity.builder()
                .id(51L)
                .email("deleted@example.com")
                .enabled(false)
                .tokenVersion(4)
                .build();
        given(deletionPreparationService.disableForDeletion(51L)).willReturn(disabledUser);
        given(userRepository.findById(51L)).willReturn(Optional.of(disabledUser));
        given(likeRepository.findByUser(disabledUser)).willReturn(List.of());
        given(commentRepository.findByAuthor(disabledUser)).willReturn(List.of());
        given(cheerPostRepository.findByAuthor(disabledUser)).willReturn(List.of());

        adminService.deleteUser(51L, null);

        InOrder order = inOrder(deletionPreparationService, userRepository, partyService);
        order.verify(deletionPreparationService).disableForDeletion(51L);
        order.verify(userRepository).findById(51L);
        order.verify(partyService).handleUserDeletion(51L);
    }
}
