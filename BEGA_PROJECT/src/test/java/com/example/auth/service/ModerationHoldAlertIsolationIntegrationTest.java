package com.example.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

import com.example.cheerboard.repo.CheerReportRepo;
import jakarta.persistence.QueryTimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Proves the alert runs in its own transaction: a failing hold check must not mark the caller's
 * transaction rollback-only, so the content retry that calls it can still commit.
 */
@DataJpaTest
@Import(ModerationHoldAlertService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ModerationHoldAlertIsolationIntegrationTest {
    @Autowired ModerationHoldAlertService alerts;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoSpyBean CheerReportRepo reports;

    @Test
    void failedHoldCheckDoesNotMarkOuterTransactionRollbackOnly() {
        doThrow(new QueryTimeoutException("slow")).when(reports).existsOpenModerationOlderThan(eq(7L), any());

        Boolean rollbackOnly = new TransactionTemplate(transactionManager).execute(status -> {
            assertThatThrownBy(() -> alerts.alertIfStale(7L)).isInstanceOf(QueryTimeoutException.class);
            return status.isRollbackOnly();
        });

        assertThat(rollbackOnly).isFalse();
    }
}
