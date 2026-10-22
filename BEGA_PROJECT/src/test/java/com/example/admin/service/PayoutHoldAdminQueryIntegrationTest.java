package com.example.admin.service;

import com.example.admin.dto.PayoutHoldPageDto;
import com.example.mate.entity.MateLinkReconciliationCase;
import com.example.mate.entity.MateLinkReconciliationReasonCode;
import com.example.mate.repository.MateLinkReconciliationCaseRepository;
import com.example.mate.entity.PayoutTransaction;
import com.example.mate.entity.SettlementStatus;
import com.example.mate.repository.PayoutTransactionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(MateLinkReconciliationCaseAdminService.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:payout_hold_admin_query;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class PayoutHoldAdminQueryIntegrationTest {

    private static final String FAILURE_CODE = "PAYOUT_MANUAL_RECONCILIATION_REQUIRED";

    @Autowired
    private MateLinkReconciliationCaseAdminService adminService;

    @Autowired
    private PayoutTransactionRepository payoutTransactionRepository;

    @Autowired
    private MateLinkReconciliationCaseRepository reconciliationCaseRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void listsManualReconciliationPayoutsInStablePages() {
        PayoutTransaction olderHold = savePayout(1201L, SettlementStatus.REQUESTED, FAILURE_CODE);
        PayoutTransaction newerHold = savePayout(1202L, SettlementStatus.REQUESTED, FAILURE_CODE);
        PayoutTransaction failedHold = savePayout(1203L, SettlementStatus.FAILED, FAILURE_CODE);
        savePayout(1204L, SettlementStatus.REQUESTED, "OTHER_FAILURE");

        Instant tiedUpdatedAt = Instant.parse("2026-10-18T00:00:00Z");
        jdbcTemplate.update("UPDATE payout_transactions SET updated_at = ? WHERE id = ?",
                Timestamp.from(tiedUpdatedAt), olderHold.getId());
        jdbcTemplate.update("UPDATE payout_transactions SET updated_at = ? WHERE id = ?",
                Timestamp.from(Instant.parse("2026-10-19T00:00:00Z")), newerHold.getId());
        jdbcTemplate.update("UPDATE payout_transactions SET updated_at = ? WHERE id = ?",
                Timestamp.from(tiedUpdatedAt), failedHold.getId());
        entityManager.clear();

        PayoutHoldPageDto firstPage = adminService.listPayoutHolds(0, 1);
        PayoutHoldPageDto secondPage = adminService.listPayoutHolds(1, 1);

        assertThat(firstPage.items()).hasSize(1);
        assertThat(firstPage.items().getFirst().payoutId()).isEqualTo(newerHold.getId());
        assertThat(firstPage.items().getFirst().paymentTransactionId()).isEqualTo(1202L);
        assertThat(firstPage.items().getFirst().status()).isEqualTo(SettlementStatus.REQUESTED);
        assertThat(firstPage.items().getFirst().reasonCode()).isEqualTo(FAILURE_CODE);
        assertThat(firstPage.items().getFirst().updatedAt()).isEqualTo(Instant.parse("2026-10-19T00:00:00Z"));
        assertThat(firstPage.page()).isZero();
        assertThat(firstPage.size()).isEqualTo(1);
        assertThat(firstPage.totalElements()).isEqualTo(3);
        assertThat(secondPage.items()).hasSize(1);
        assertThat(secondPage.items().getFirst().payoutId()).isEqualTo(failedHold.getId());
        assertThat(secondPage.items().getFirst().updatedAt()).isEqualTo(tiedUpdatedAt);
        assertThat(secondPage.totalPages()).isEqualTo(3);
        PayoutHoldPageDto thirdPage = adminService.listPayoutHolds(2, 1);
        assertThat(thirdPage.items()).singleElement()
                .satisfies(item -> assertThat(item.payoutId()).isEqualTo(olderHold.getId()));
    }

    @Test
    void includesManualReconciliationPayoutsRegardlessOfStatus() {
        for (SettlementStatus status : SettlementStatus.values()) {
            savePayout(1300L + status.ordinal(), status, FAILURE_CODE);
        }
        savePayout(1400L, SettlementStatus.COMPLETED, "OTHER_FAILURE");

        PayoutHoldPageDto page = adminService.listPayoutHolds(0, 100);

        assertThat(page.items()).hasSize(SettlementStatus.values().length);
        assertThat(page.items())
                .extracting(item -> item.status())
                .containsExactlyInAnyOrder(SettlementStatus.values());
        assertThat(page.items())
                .allSatisfy(item -> assertThat(item.reasonCode()).isEqualTo(FAILURE_CODE));
    }

    @Test
    void capsPageSizeAndClampsInvalidPagingInputs() {
        savePayout(2201L, SettlementStatus.REQUESTED, FAILURE_CODE);
        savePayout(2202L, SettlementStatus.REQUESTED, FAILURE_CODE);

        PayoutHoldPageDto oversized = adminService.listPayoutHolds(0, 1000);
        PayoutHoldPageDto invalid = adminService.listPayoutHolds(-1, 0);
        PayoutHoldPageDto deeplyPaged = adminService.listPayoutHolds(Integer.MAX_VALUE, 100);

        assertThat(oversized.size()).isEqualTo(100);
        assertThat(oversized.items()).hasSize(2);
        assertThat(invalid.page()).isZero();
        assertThat(invalid.size()).isEqualTo(1);
        assertThat(invalid.items()).hasSize(1);
        assertThat(deeplyPaged.page()).isEqualTo(1000);
        assertThat((long) deeplyPaged.page() * deeplyPaged.size()).isLessThanOrEqualTo(100_000L);
    }

    @Test
    void capsReconciliationCaseOffsetAndPageSize() {
        saveReconciliationCase("test-case", Instant.parse("2026-10-20T00:00:00Z"));

        var deeplyPaged = adminService.list(Integer.MAX_VALUE, 100);
        var oversized = adminService.list(0, 1000);

        assertThat(deeplyPaged.page()).isEqualTo(1000);
        assertThat((long) deeplyPaged.page() * deeplyPaged.size()).isLessThanOrEqualTo(100_000L);
        assertThat(oversized.size()).isEqualTo(100);
    }

    @Test
    void mapsReconciliationCasesAndUsesIdAsStableTimestampTieBreaker() {
        Instant observedAt = Instant.parse("2026-10-20T00:00:00Z");
        MateLinkReconciliationCase older = saveReconciliationCase("case-older-id", observedAt);
        MateLinkReconciliationCase newer = saveReconciliationCase("case-newer-id", observedAt);

        var firstPage = adminService.list(0, 1);
        var secondPage = adminService.list(1, 1);

        assertThat(firstPage.items()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(newer.getId());
            assertThat(item.reasonCode()).isEqualTo(MateLinkReconciliationReasonCode.PAYMENT_TRANSACTION_MISSING);
            assertThat(item.observedAt()).isEqualTo(observedAt);
        });
        assertThat(secondPage.items()).singleElement().satisfies(item -> assertThat(item.id()).isEqualTo(older.getId()));
    }

    private MateLinkReconciliationCase saveReconciliationCase(String caseKey, Instant observedAt) {
        return reconciliationCaseRepository.saveAndFlush(MateLinkReconciliationCase.builder()
                .caseKey(caseKey)
                .reasonCode(MateLinkReconciliationReasonCode.PAYMENT_TRANSACTION_MISSING)
                .observedAt(observedAt)
                .build());
    }

    private PayoutTransaction savePayout(long paymentTransactionId, SettlementStatus status, String failureCode) {
        return payoutTransactionRepository.saveAndFlush(PayoutTransaction.builder()
                .paymentTransactionId(paymentTransactionId)
                .sellerId(700L)
                .requestedAmount(5000)
                .status(status)
                .failureCode(failureCode)
                .retryCount(0)
                .recoveryOffsetAmount(0)
                .build());
    }
}
