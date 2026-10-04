package com.example.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;

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
 * {@link AdminService#deleteUser} 를 실제 트랜잭션 경계(바깥 트랜잭션 + REQUIRES_NEW 준비 단계)로 돌린다.
 * 단위 테스트는 서비스를 mock 해서 영속성 컨텍스트 상호작용을 검증하지 못한다.
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
    @MockitoBean private CacheManager cacheManager;
    @MockitoBean private AuditLogRepository auditLogRepository;
    @MockitoBean private AdminNonCanonicalCleanupTrackerRepository nonCanonicalCleanupTrackerRepository;
    @MockitoBean private PartyService partyService;
    @MockitoBean private RefreshRepository refreshRepository;
    @MockitoBean private PredictionService predictionService;
    @MockitoBean private AdminMateDeletionGuard mateDeletionGuard;

    @Test
    void laterWritesToTheUserInTheSameTransactionDoNotReEnableTheDisabledAccount() {
        UserEntity admin = saveUser("ROLE_SUPER_ADMIN", "admin");
        UserEntity target = saveUser("ROLE_USER", "target");
        // 삭제 정리 단계에서 같은 트랜잭션이 사용자 행을 수정하는 상황. 바깥 영속성 컨텍스트가
        // REQUIRES_NEW 커밋 이전 상태(enabled=true)를 들고 있으면, 이 수정의 UPDATE 가 그 값을 되쓴다.
        doAnswer(invocation -> {
            UserEntity managed = userRepository.findById(target.getId()).orElseThrow();
            managed.setName("mutated-during-cleanup");
            return null;
        }).when(partyService).handleUserDeletion(target.getId());

        adminService.deleteUser(target.getId(), admin.getId());

        UserEntity reloaded = userRepository.findById(target.getId()).orElseThrow();
        assertThat(reloaded.getName()).isEqualTo("mutated-during-cleanup");
        assertThat(reloaded.isEnabled()).isFalse();
        assertThat(reloaded.getTokenVersion()).isEqualTo(target.getTokenVersion() + 1);
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
