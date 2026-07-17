package com.example.mate;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class PaymentRefundReconciliationMigrationSqlTest {

    @Test
    @DisplayName("PostgreSQL refund reconciliation migration is additive and repeatable")
    void postgresMigrationAddsReconciliationFields() throws IOException {
        String sql = load("db/migration_postgresql/V175__add_mate_refund_intent_and_seller_recovery.sql");

        assertThat(sql)
                .contains("ADD COLUMN IF NOT EXISTS requested_refund_amount")
                .contains("ADD COLUMN IF NOT EXISTS requested_fee_amount")
                .contains("ADD COLUMN IF NOT EXISTS cancellation_requested_at TIMESTAMPTZ")
                .contains("ADD COLUMN IF NOT EXISTS provider_reconciled_at TIMESTAMPTZ")
                .contains("ADD COLUMN IF NOT EXISTS recovery_offset_amount INTEGER NOT NULL DEFAULT 0")
                .contains("ADD COLUMN IF NOT EXISTS recovery_offset_reserved_at TIMESTAMPTZ")
                .contains("CREATE TABLE IF NOT EXISTS seller_payout_recoveries")
                .contains("uq_spr_source_payment")
                .contains("idx_spr_seller_status");
    }

    @Test
    @DisplayName("Oracle refund reconciliation migration guards table and columns")
    void oracleMigrationAddsReconciliationFieldsIdempotently() throws IOException {
        String sql = load("db/migration/V169__add_mate_refund_intent_and_seller_recovery.sql")
                .toUpperCase();

        assertThat(sql)
                .contains("USER_TABLES")
                .contains("PAYMENT_TRANSACTIONS")
                .contains("REQUESTED_REFUND_AMOUNT NUMBER(10)")
                .contains("REQUESTED_FEE_AMOUNT NUMBER(10)")
                .contains("CANCELLATION_REQUESTED_AT TIMESTAMP(6) WITH TIME ZONE")
                .contains("PROVIDER_RECONCILED_AT TIMESTAMP(6) WITH TIME ZONE")
                .contains("RECOVERY_OFFSET_AMOUNT NUMBER(10) DEFAULT 0 NOT NULL")
                .contains("RECOVERY_OFFSET_RESERVED_AT TIMESTAMP(6) WITH TIME ZONE")
                .contains("CREATE TABLE SELLER_PAYOUT_RECOVERIES")
                .contains("CREATE UNIQUE INDEX UQ_SPR_SOURCE_PAYMENT")
                .contains("CREATE INDEX IDX_SPR_SELLER_STATUS");
    }

    @Test
    @DisplayName("Follow-up migrations add offset ledger and quarantine unverified legacy completions")
    void followUpMigrationsRequireVerifiedBackfill() throws IOException {
        String postgres = load("db/migration_postgresql/V176__add_recovery_offset_ledger_and_verified_backfill.sql");
        String oracle = load("db/migration/V170__add_recovery_offset_ledger_and_verified_backfill.sql")
                .toUpperCase();

        assertThat(postgres)
                .contains("MATE_PAYOUT_DUPLICATES_REQUIRE_MANUAL_RECONCILIATION")
                .contains("MATE_LEGACY_PAYOUT_COMPLETION_REQUIRES_MANUAL_RECONCILIATION")
                .contains("MATE_LEGACY_RECOVERY_OFFSET_REQUIRES_MANUAL_RECONCILIATION")
                .contains("MATE_ACTIVE_PAYOUT_REQUIRES_MANUAL_RECONCILIATION")
                .contains("COALESCE(recovery_offset_amount, 0) <> 0")
                .contains("ADD COLUMN IF NOT EXISTS provider_code")
                .contains("ADD COLUMN IF NOT EXISTS provider_seller_id")
                .contains("ADD COLUMN IF NOT EXISTS claim_protocol")
                .contains("ck_payout_offset_protocol")
                .contains("ck_payout_requested_protocol")
                .contains("ck_payout_completion_verified")
                .contains("COALESCE(claim_protocol, '') = 'SNAPSHOT_V1'")
                .contains("COALESCE(failure_code, '') = 'PAYOUT_COMPLETION_VERIFIED'")
                .contains("CREATE TABLE IF NOT EXISTS seller_recovery_offset_allocations")
                .contains("idx_payout_status_next_retry")
                .contains("PAYOUT_COMPLETION_VERIFIED")
                .contains("INSERT INTO seller_payout_recoveries");
        assertThat(oracle)
                .contains("MATE_PAYOUT_DUPLICATES_REQUIRE_MANUAL_RECONCILIATION")
                .contains("MATE_LEGACY_PAYOUT_COMPLETION_REQUIRES_MANUAL_RECONCILIATION")
                .contains("MATE_LEGACY_RECOVERY_OFFSET_REQUIRES_MANUAL_RECONCILIATION")
                .contains("MATE_ACTIVE_PAYOUT_REQUIRES_MANUAL_RECONCILIATION")
                .contains("NVL(RECOVERY_OFFSET_AMOUNT, 0) <> 0")
                .contains("PROVIDER_CODE VARCHAR2(30)")
                .contains("PROVIDER_SELLER_ID VARCHAR2(200)")
                .contains("CLAIM_PROTOCOL VARCHAR2(30)")
                .contains("CK_PAYOUT_OFFSET_PROTOCOL")
                .contains("CK_PAYOUT_REQUESTED_PROTOCOL")
                .contains("CK_PAYOUT_COMPLETION_VERIFIED")
                .contains("NVL(CLAIM_PROTOCOL, '-') = 'SNAPSHOT_V1'")
                .contains("NVL(FAILURE_CODE, '-') = 'PAYOUT_COMPLETION_VERIFIED'")
                .contains("CREATE TABLE SELLER_RECOVERY_OFFSET_ALLOCATIONS")
                .contains("CREATE INDEX IDX_PAYOUT_STATUS_NEXT_RETRY")
                .contains("PAYOUT_COMPLETION_VERIFIED")
                .contains("REFUNDED_AFTER_SETTLEMENT")
                .contains("MERGE INTO SELLER_PAYOUT_RECOVERIES");
        assertThat(postgres.indexOf("ADD COLUMN IF NOT EXISTS claim_protocol"))
                .isLessThan(postgres.indexOf("ck_payout_offset_protocol"));
        assertThat(oracle.indexOf("CLAIM_PROTOCOL VARCHAR2(30)"))
                .isLessThan(oracle.indexOf("CK_PAYOUT_OFFSET_PROTOCOL"));
    }

    private String load(String resourcePath) throws IOException {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        try (var inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
