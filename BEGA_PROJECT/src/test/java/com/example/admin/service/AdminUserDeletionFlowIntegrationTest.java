package com.example.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.example.admin.repository.AdminNonCanonicalCleanupTrackerRepository;
import com.example.admin.repository.AuditLogRepository;
import com.example.auth.entity.UserEntity;
import com.example.auth.repository.RefreshRepository;
import com.example.auth.repository.UserRepository;
import com.example.auth.service.AccountDeletionContentService;
import com.example.auth.service.AccountDeletionFinalizationService;
import com.example.cheerboard.repo.CheerCommentRepo;
import com.example.cheerboard.repo.CheerPostLikeRepo;
import com.example.cheerboard.repo.CheerPostRepo;
import com.example.cheerboard.repo.CheerReportRepo;
import com.example.cheerboard.service.CheerPostService;
import com.example.mate.repository.PartyRepository;
import com.example.mate.service.PartyService;
import com.example.prediction.PredictionService;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link AdminService#deleteUser} 의 REQUIRES_NEW 준비 단계가 finalizer 실패와 분리되어 due marker를
 * 보존하는지 확인한다.
 */
@DataJpaTest
@Import({AdminService.class, AdminUserDeletionPreparationService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AdminUserDeletionFlowIntegrationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private AdminService adminService;

    @MockitoBean private CheerPostRepo cheerPostRepository;
    @MockitoBean private CheerReportRepo cheerReportRepo;
    @MockitoBean private PartyRepository partyRepository;
    @MockitoBean private CheerCommentRepo commentRepository;
    @MockitoBean private CheerPostLikeRepo likeRepository;
    @MockitoBean private CheerPostService cheerPostService;
    @MockitoBean private CacheManager cacheManager;
    @MockitoBean private AuditLogRepository auditLogRepository;
    @MockitoBean private AdminNonCanonicalCleanupTrackerRepository nonCanonicalCleanupTrackerRepository;
    @MockitoBean private PartyService partyService;
    @MockitoBean private RefreshRepository refreshRepository;
    @MockitoBean private PredictionService predictionService;
    @MockitoBean private AdminMateDeletionGuard mateDeletionGuard;
    @MockitoBean private AccountDeletionContentService accountDeletionContentService;
    @MockitoBean private AccountDeletionFinalizationService deletionFinalizationService;

    @Test
    void adminDeletionCommitsDueRetryMarkerBeforeFinalization() {
        UserEntity admin = saveUser("ROLE_SUPER_ADMIN", "admin");
        UserEntity target = saveUser("ROLE_USER", "target");
        adminService.deleteUser(target.getId(), admin.getId());

        UserEntity reloaded = userRepository.findById(target.getId()).orElseThrow();
        assertThat(reloaded.getName()).isEqualTo(target.getName());
        assertThat(reloaded.isEnabled()).isFalse();
        assertThat(reloaded.getTokenVersion()).isEqualTo(target.getTokenVersion() + 1);
        assertThat(reloaded.isPendingDeletion()).isTrue();
        assertThat(reloaded.getDeletionRequestedAt()).isNull();
        assertThat(reloaded.getDeletionScheduledFor()).isNotNull().isBeforeOrEqualTo(LocalDateTime.now());
        org.mockito.Mockito.verify(deletionFinalizationService)
                .finalizeImmediatelyAfterAdminDeletion(target.getId(), admin.getId());
    }

    @Test
    void deniedDeletionOfAnAdministratorLeavesTheTargetUntouched() {
        UserEntity actingAdmin = saveUser("ROLE_SUPER_ADMIN", "admin");
        UserEntity otherAdmin = saveUser("ROLE_ADMIN", "other");

        assertThatThrownBy(() -> adminService.deleteUser(otherAdmin.getId(), actingAdmin.getId()))
                .isInstanceOf(AccessDeniedException.class);

        UserEntity reloaded = userRepository.findById(otherAdmin.getId()).orElseThrow();
        assertThat(reloaded.isEnabled()).isTrue();
        assertThat(reloaded.getTokenVersion()).isEqualTo(otherAdmin.getTokenVersion());
    }

    @Test
    void deniedSelfDeletionLeavesTheActingAdministratorUntouched() {
        UserEntity ordinaryUser = saveUser("ROLE_USER", "self");

        assertThatThrownBy(() -> adminService.deleteUser(ordinaryUser.getId(), ordinaryUser.getId()))
                .isInstanceOf(AccessDeniedException.class);

        UserEntity reloaded = userRepository.findById(ordinaryUser.getId()).orElseThrow();
        assertThat(reloaded.isEnabled()).isTrue();
        assertThat(reloaded.getTokenVersion()).isEqualTo(ordinaryUser.getTokenVersion());
    }

    private UserEntity saveUser(String role, String prefix) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return userRepository.saveAndFlush(UserEntity.builder()
                .uniqueId(UUID.randomUUID())
                .handle("@" + prefix + suffix)
                .name("Synthetic " + prefix)
                .email(prefix + "-" + suffix + "@example.test")
                .role(role)
                .enabled(true)
                .tokenVersion(3)
                .build());
    }
}
