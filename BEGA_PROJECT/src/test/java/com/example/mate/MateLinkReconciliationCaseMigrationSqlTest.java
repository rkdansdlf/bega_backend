package com.example.mate;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class MateLinkReconciliationCaseMigrationSqlTest {

    @Test
    void oracleAndPostgresMigrationsCreateAnIdempotentInternalIdOnlyLedger() throws Exception {
        String oracle = Files.readString(Path.of(
                "src/main/resources/db/migration/V186__create_mate_link_reconciliation_cases.sql"))
                .toLowerCase(Locale.ROOT);
        String postgres = Files.readString(Path.of(
                "src/main/resources/db/migration_postgresql/V191__create_mate_link_reconciliation_cases.sql"))
                .toLowerCase(Locale.ROOT);

        for (String sql : new String[] {oracle, postgres}) {
            assertThat(sql).contains(
                    "create table mate_link_recon_cases",
                    "case_key",
                    "party_id",
                    "application_id",
                    "payment_transaction_id",
                    "reason_code",
                    "observed_at",
                    "unique (case_key)",
                    "idx_mlrc_observed_id");
            assertThat(sql).doesNotContain(
                    "payment_key",
                    "order_id",
                    "email",
                    "phone",
                    "provider_ref",
                    "provider_payload");
            assertThat(sql).doesNotContain("drop ", "truncate ", "delete from");
        }
    }
}
