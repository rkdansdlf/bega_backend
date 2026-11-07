package com.example.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;

import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Proves the purge-hold clock is saved even when the content step clears the persistence context. The retry must
 * write to the row it re-reads after that step, not to the instance it loaded before it.
 */
@DataJpaTest
@Import({AccountContentAnonymizationRetryService.class, ModerationHoldAlertService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AccountContentAnonymizationRetryPurgeHoldIntegrationTest {
    @Autowired AccountContentAnonymizationRetryService retryService;
    @Autowired UserRepository users;
    @Autowired EntityManager entityManager;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoBean AccountDeletionContentService content;

    @Test
    void purgeHoldClockIsSavedWhenTheContentStepClearsThePersistenceContext() {
        Long userId = newDueTombstone();
        doAnswer(invocation -> {
            entityManager.clear();
            return AccountDeletionContentService.Result.PURGE_PENDING;
        }).when(content).anonymize(anyLong());

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> retryService.retry(userId));

        UserEntity reloaded = new TransactionTemplate(transactionManager)
                .execute(status -> users.findById(userId).orElseThrow());
        assertThat(reloaded.getPurgeHoldStartedAt()).isNotNull();
        assertThat(reloaded.getContentAnonymizationRetryAt()).isNotNull();
    }

    private Long newDueTombstone() {
        return new TransactionTemplate(transactionManager).execute(status -> {
            UserEntity user = users.saveAndFlush(UserEntity.builder().uniqueId(UUID.randomUUID())
                    .handle("h" + UUID.randomUUID().toString().substring(0, 8))
                    .name("탈퇴한 사용자").email("pending@example.invalid").role("ROLE_USER")
                    .enabled(false).locked(true).pendingDeletion(true)
                    .contentAnonymizationRetryAt(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1)).build());
            user.setEmail("deleted-" + user.getId() + "@deleted.invalid");
            return users.saveAndFlush(user).getId();
        });
    }
}
