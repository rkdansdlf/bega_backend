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
import java.util.Optional;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceUserDeletionAuthorizationTest {

    @Mock private UserRepository userRepository;
    @Mock private CheerPostRepo cheerPostRepository;
    @Mock private CheerReportRepo cheerReportRepo;
    @Mock private PartyRepository partyRepository;
    @Mock private CheerCommentRepo commentRepository;
    @Mock private CheerPostLikeRepo likeRepository;
    @Mock private CacheManager cacheManager;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private AdminNonCanonicalCleanupTrackerRepository trackerRepository;
    @Mock private PartyService partyService;
    @Mock private RefreshRepository refreshRepository;
    @Mock private PredictionService predictionService;
    @Mock private AdminUserDeletionPreparationService deletionPreparationService;
    @Mock private AdminMateDeletionGuard mateDeletionGuard;

    @InjectMocks
    private AdminService adminService;

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_ADMIN", "ROLE_SUPER_ADMIN", "ROLE_UNKNOWN"})
    void deleteUserRejectsAdministratorTargetsBeforeAnyDeletionSideEffect(String targetRole) {
        UserEntity target = user(22L, targetRole);
        when(userRepository.findById(22L)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> adminService.deleteUser(22L, 11L))
                .isInstanceOf(AccessDeniedException.class);

        verify(deletionPreparationService, never()).disableForDeletion(22L, 11L);
        verifyNoInteractions(
                cheerPostRepository,
                cheerReportRepo,
                partyRepository,
                commentRepository,
                likeRepository,
                cacheManager,
                auditLogRepository,
                trackerRepository,
                partyService,
                refreshRepository,
                predictionService);
    }

    @Test
    void deleteUserRejectsTheActingAdministratorBeforeAnyDeletionSideEffect() {
        when(userRepository.findById(11L)).thenReturn(Optional.of(user(11L, "ROLE_USER")));

        assertThatThrownBy(() -> adminService.deleteUser(11L, 11L))
                .isInstanceOf(AccessDeniedException.class);

        verify(deletionPreparationService, never()).disableForDeletion(11L, 11L);
        verifyNoInteractions(
                cheerPostRepository,
                cheerReportRepo,
                partyRepository,
                commentRepository,
                likeRepository,
                cacheManager,
                auditLogRepository,
                trackerRepository,
                partyService,
                refreshRepository,
                predictionService);
    }

    @Test
    void deleteUserStillAllowsAnAdministratorToDeleteAnOrdinaryUser() {
        UserEntity target = user(22L, "ROLE_USER");
        when(userRepository.findById(22L)).thenReturn(Optional.of(target));
        when(deletionPreparationService.disableForDeletion(22L, 11L)).thenReturn(target);

        adminService.deleteUser(22L, 11L);

        verify(deletionPreparationService).disableForDeletion(22L, 11L);
        verify(partyService).handleUserDeletion(22L);
    }

    private UserEntity user(Long id, String role) {
        return UserEntity.builder()
                .id(id)
                .email("user-" + id + "@example.test")
                .role(role)
                .build();
    }
}
