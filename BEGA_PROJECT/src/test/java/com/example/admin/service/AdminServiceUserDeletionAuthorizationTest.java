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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    // 권한 판단은 대상 행을 잠근 REQUIRES_NEW 준비 단계가 한다(역할·본인 케이스는
    // AdminUserDeletionPreparationServiceTest, 실제 트랜잭션 경계는 AdminUserDeletionFlowIntegrationTest).
    // 여기서는 그 거부가 그대로 전파되고, 어떤 삭제 부작용도 시작되지 않으며, 거부 전에 사용자를
    // 바깥 영속성 컨텍스트로 읽지 않는다는 점을 확인한다.
    @Test
    void deleteUserPropagatesTheLockedRecheckDenialBeforeAnyDeletionSideEffect() {
        when(deletionPreparationService.disableForDeletion(22L, 11L))
                .thenThrow(new AccessDeniedException("대상 계정은 삭제할 수 없습니다."));

        assertThatThrownBy(() -> adminService.deleteUser(22L, 11L))
                .isInstanceOf(AccessDeniedException.class);

        verify(deletionPreparationService).disableForDeletion(22L, 11L);
        verifyNoInteractions(
                userRepository,
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
