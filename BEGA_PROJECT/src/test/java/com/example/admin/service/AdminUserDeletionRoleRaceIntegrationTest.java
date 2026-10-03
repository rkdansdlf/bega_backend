package com.example.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.admin.exception.InvalidRoleChangeException;
import com.example.admin.repository.AuditLogRepository;
import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserRepository;
import com.example.auth.service.RefreshTokenRevocationService;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@Import({AdminRoleService.class, AdminUserDeletionPreparationService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AdminUserDeletionRoleRaceIntegrationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private AdminRoleService adminRoleService;
    @Autowired private AdminUserDeletionPreparationService deletionPreparationService;
    @Autowired private PlatformTransactionManager transactionManager;

    @MockitoBean private AuditLogRepository auditLogRepository;
    @MockitoBean private RefreshTokenRevocationService refreshTokenRevocationService;

    @Test
    void promotionCommittedAfterDeletionPreflightIsRejectedByLockedRecheck() throws Exception {
        UserEntity admin = saveUser("ROLE_SUPER_ADMIN", "admin");
        UserEntity target = saveUser("ROLE_USER", "target");
        CountDownLatch preflightComplete = new CountDownLatch(1);
        CountDownLatch continueDeletion = new CountDownLatch(1);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        TransactionTemplate outerTransaction = new TransactionTemplate(transactionManager);
        outerTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);

        try {
            Future<String> deletion = worker.submit(() -> outerTransaction.execute(status -> {
                UserEntity preflightTarget = userRepository.findById(target.getId()).orElseThrow();
                assertThat(preflightTarget.isAdmin()).isFalse();
                preflightComplete.countDown();
                try {
                    if (!continueDeletion.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting for synthetic role change");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted during synthetic role-change race", e);
                }

                try {
                    deletionPreparationService.disableForDeletion(target.getId(), admin.getId());
                    return "deleted";
                } catch (AccessDeniedException expected) {
                    return "denied";
                }
            }));

            assertThat(preflightComplete.await(5, TimeUnit.SECONDS)).isTrue();
            adminRoleService.promoteToAdmin(admin.getId(), target.getId(), "synthetic role-change race");
            continueDeletion.countDown();

            assertThat(deletion.get(10, TimeUnit.SECONDS)).isEqualTo("denied");
        } finally {
            continueDeletion.countDown();
            worker.shutdownNow();
        }

        UserEntity unchangedTarget = userRepository.findById(target.getId()).orElseThrow();
        assertThat(unchangedTarget.getRole()).isEqualTo("ROLE_ADMIN");
        assertThat(unchangedTarget.isEnabled()).isTrue();
        assertThat(unchangedTarget.getTokenVersion()).isEqualTo(target.getTokenVersion());
    }

    @Test
    void promotionAfterDeletionPreparationCannotTurnDisabledTargetIntoAdministrator() {
        UserEntity admin = saveUser("ROLE_SUPER_ADMIN", "admin");
        UserEntity target = saveUser("ROLE_USER", "target");

        deletionPreparationService.disableForDeletion(target.getId(), admin.getId());

        assertThatThrownBy(() -> adminRoleService.promoteToAdmin(
                admin.getId(), target.getId(), "synthetic reverse-order race"))
                .isInstanceOf(InvalidRoleChangeException.class);

        UserEntity unchangedTarget = userRepository.findById(target.getId()).orElseThrow();
        assertThat(unchangedTarget.getRole()).isEqualTo("ROLE_USER");
        assertThat(unchangedTarget.isEnabled()).isFalse();
        verify(refreshTokenRevocationService, never()).revokeAllSessionsForUser(target.getId());
        verify(auditLogRepository, never()).save(org.mockito.ArgumentMatchers.any());
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
