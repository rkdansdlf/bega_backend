package com.example.admin.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.admin.entity.AuditLog;
import com.example.auth.entity.UserEntity;
import com.example.auth.entity.UserProvider;
import com.example.auth.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class AuditLogRepositoryDeletionRedactionIntegrationTest {

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    @Test
    void redactsDeletedUserFreeTextAndPreservesAuditReferencesAndModerationStatus() {
        String email = "person@example.test";
        AuditLog deletedUser = auditLogRepository.saveAndFlush(AuditLog.builder()
                .adminId(7L)
                .targetUserId(44L)
                .action(AuditLog.AuditAction.DELETE_USER)
                .oldValue(email)
                .description("사용자 삭제")
                .createdAt(LocalDateTime.now())
                .build());
        AuditLog deletedPost = auditLogRepository.saveAndFlush(AuditLog.builder()
                .adminId(7L)
                .targetUserId(44L)
                .action(AuditLog.AuditAction.DELETE_POST)
                .oldValue("synthetic authored text with private@example.test")
                .description("게시글 삭제 (ID: 9001)")
                .createdAt(LocalDateTime.now())
                .build());
        AuditLog deletedMate = auditLogRepository.saveAndFlush(AuditLog.builder()
                .adminId(7L)
                .targetUserId(44L)
                .action(AuditLog.AuditAction.DELETE_MATE)
                .oldValue("synthetic party description with private@example.test")
                .description("메이트 모임 삭제 (ID: 9002)")
                .createdAt(LocalDateTime.now())
                .build());
        AuditLog roleChange = auditLogRepository.saveAndFlush(AuditLog.builder()
                .adminId(7L)
                .targetUserId(44L)
                .action(AuditLog.AuditAction.PROMOTE_TO_ADMIN)
                .oldValue("ROLE_USER")
                .newValue("ROLE_ADMIN")
                .description("synthetic free-form reason private@example.test")
                .createdAt(LocalDateTime.now())
                .build());
        AuditLog moderationEvent = auditLogRepository.saveAndFlush(AuditLog.builder()
                .adminId(7L)
                .targetUserId(44L)
                .action(AuditLog.AuditAction.WARN_REPEATED_INFRACTION)
                .oldValue("REQUESTED")
                .newValue("REVIEWING")
                .description("신고 케이스 처리 (ID: 9003)")
                .createdAt(LocalDateTime.now())
                .build());
        AuditLog otherTarget = auditLogRepository.saveAndFlush(AuditLog.builder()
                .adminId(7L)
                .targetUserId(45L)
                .action(AuditLog.AuditAction.DELETE_POST)
                .oldValue("unrelated private@example.test")
                .description("게시글 삭제 (ID: 9004)")
                .createdAt(LocalDateTime.now())
                .build());

        int redactedValues = auditLogRepository.redactDeletedUserAuditValues(
                44L,
                java.util.List.of(
                        AuditLog.AuditAction.DELETE_USER,
                        AuditLog.AuditAction.DELETE_POST,
                        AuditLog.AuditAction.DELETE_MATE),
                "[REDACTED]");
        int redactedDescriptions = auditLogRepository.redactDeletedUserAuditDescriptions(
                44L,
                java.util.List.of(
                        AuditLog.AuditAction.PROMOTE_TO_ADMIN,
                        AuditLog.AuditAction.DEMOTE_TO_USER),
                "[REDACTED]");

        entityManager.clear();
        AuditLog redactedUser = auditLogRepository.findById(deletedUser.getId()).orElseThrow();
        AuditLog redactedPost = auditLogRepository.findById(deletedPost.getId()).orElseThrow();
        AuditLog redactedMate = auditLogRepository.findById(deletedMate.getId()).orElseThrow();
        AuditLog redactedRoleChange = auditLogRepository.findById(roleChange.getId()).orElseThrow();
        AuditLog unchangedModerationEvent = auditLogRepository.findById(moderationEvent.getId()).orElseThrow();
        AuditLog unchangedOtherTarget = auditLogRepository.findById(otherTarget.getId()).orElseThrow();

        assertThat(redactedValues).isEqualTo(3);
        assertThat(redactedDescriptions).isEqualTo(1);
        assertThat(redactedUser.getOldValue()).isEqualTo("[REDACTED]");
        assertThat(redactedPost.getOldValue()).isEqualTo("[REDACTED]");
        assertThat(redactedMate.getOldValue()).isEqualTo("[REDACTED]");
        assertThat(redactedRoleChange.getDescription()).isEqualTo("[REDACTED]");
        assertThat(redactedRoleChange.getOldValue()).isEqualTo("ROLE_USER");
        assertThat(redactedRoleChange.getNewValue()).isEqualTo("ROLE_ADMIN");

        for (AuditLog audit : java.util.List.of(redactedUser, redactedPost, redactedMate, redactedRoleChange)) {
            assertThat(audit.getAdminId()).isEqualTo(7L);
            assertThat(audit.getTargetUserId()).isEqualTo(44L);
            assertThat(audit.getCreatedAt()).isNotNull();
        }
        assertThat(redactedPost.getAction()).isEqualTo(AuditLog.AuditAction.DELETE_POST);
        assertThat(redactedPost.getDescription()).isEqualTo("게시글 삭제 (ID: 9001)");
        assertThat(redactedMate.getAction()).isEqualTo(AuditLog.AuditAction.DELETE_MATE);
        assertThat(redactedMate.getDescription()).isEqualTo("메이트 모임 삭제 (ID: 9002)");
        assertThat(unchangedModerationEvent.getOldValue()).isEqualTo("REQUESTED");
        assertThat(unchangedModerationEvent.getNewValue()).isEqualTo("REVIEWING");
        assertThat(unchangedModerationEvent.getDescription()).isEqualTo("신고 케이스 처리 (ID: 9003)");
        assertThat(unchangedOtherTarget.getOldValue()).isEqualTo("unrelated private@example.test");
    }

    @Test
    void auditRedactionDoesNotDetachAccountBeforeLazyProviderCleanup() {
        UserEntity user = UserEntity.builder()
                .uniqueId(UUID.randomUUID())
                .handle("audit-lazy-user")
                .name("Synthetic User")
                .email("audit-lazy@example.test")
                .role("ROLE_USER")
                .build();
        user.getProviders().add(UserProvider.builder()
                .user(user)
                .provider("GOOGLE")
                .providerId("synthetic-provider-id")
                .email("audit-lazy@example.test")
                .build());
        user = userRepository.saveAndFlush(user);
        Long userId = user.getId();

        entityManager.clear();
        UserEntity managedUser = userRepository.findByIdForWrite(userId).orElseThrow();
        assertThat(entityManager.contains(managedUser)).isTrue();

        auditLogRepository.redactDeletedUserAuditValues(
                userId,
                List.of(AuditLog.AuditAction.DELETE_USER),
                "[REDACTED]");

        assertThat(entityManager.contains(managedUser)).isTrue();
        assertThat(managedUser.getProviders()).hasSize(1);
        assertThat(managedUser.getProviders().get(0).getProviderId()).isEqualTo("synthetic-provider-id");
    }
}
