package com.example.media.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

class PhysicalMediaRegistryMigrationTest {
    @ParameterizedTest
    @CsvSource({
        "PostgreSQL, db/migration_postgresql/V197__create_physical_media_lock_stripes.sql",
        "Oracle, db/migration/V191__create_physical_media_lock_stripes.sql"
    })
    void lockStripeMigrationCreatesEveryRequiredRow(String mode, String resource) throws Exception {
        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:physical_stripes_" + UUID.randomUUID().toString().replace("-", "") + ";MODE=" + mode, "sa", "")) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource(resource));
            try (var statement = connection.createStatement()) {
                try (var rows = statement.executeQuery("SELECT COUNT(*), MIN(stripe_id), MAX(stripe_id) FROM physical_media_lock_stripes")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getInt(1)).isEqualTo(64);
                    assertThat(rows.getInt(2)).isZero();
                    assertThat(rows.getInt(3)).isEqualTo(63);
                }
                assertThatThrownBy(() -> statement.executeUpdate("INSERT INTO physical_media_lock_stripes (stripe_id) VALUES (64)"))
                        .isInstanceOf(SQLException.class);
            }
        }
    }

    @ParameterizedTest
    @CsvSource({
        "PostgreSQL, db/migration_postgresql/V195__add_physical_media_reference_registry.sql",
        "Oracle, db/migration/V189__add_physical_media_reference_registry.sql"
    })
    void schemaStartsOnHoldAndRejectsInvalidClaimsAndOrphanReferences(String mode, String resource) throws Exception {
        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:physical_registry_" + UUID.randomUUID().toString().replace("-", "") + ";MODE=" + mode, "sa", "")) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource(resource));
            try (var statement = connection.createStatement()) {
                String hash = "a".repeat(64);
                String trueLiteral = mode.equals("Oracle") ? "1" : "TRUE";
                statement.executeUpdate("INSERT INTO physical_media_objects (object_hash, physical_bucket, object_key) VALUES ('" + hash + "', 'bucket', 'bucket/key')");
                try (var rows = statement.executeQuery("SELECT state, audit_generation FROM physical_media_objects WHERE object_hash='" + hash + "'")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString(1)).isEqualTo("HOLD_UNVERIFIED");
                    assertThat(rows.getLong(2)).isZero();
                }
                assertThatThrownBy(() -> statement.executeUpdate("UPDATE physical_media_objects SET state='ACTIVE' WHERE object_hash='" + hash + "'"))
                        .isInstanceOf(SQLException.class);
                assertThatThrownBy(() -> statement.executeUpdate("UPDATE physical_media_objects SET state='DELETE_CLAIMED', audit_generation=1 WHERE object_hash='" + hash + "'"))
                        .isInstanceOf(SQLException.class);
                assertThatThrownBy(() -> statement.executeUpdate("UPDATE physical_media_objects SET state='DELETE_PENDING', audit_generation=1 WHERE object_hash='" + hash + "'"))
                        .isInstanceOf(SQLException.class);
                assertThatThrownBy(() -> statement.executeUpdate("UPDATE physical_media_objects SET state='DELETED', audit_generation=1, deletion_requested=" + trueLiteral + " WHERE object_hash='" + hash + "'"))
                        .isInstanceOf(SQLException.class);
                assertThatThrownBy(() -> statement.executeUpdate("INSERT INTO physical_media_refs (source_kind, source_id, object_hash) VALUES ('POST_IMAGE', '1', 'missing')"))
                        .isInstanceOf(SQLException.class);
                statement.executeUpdate("UPDATE physical_media_objects SET state='ACTIVE', audit_generation=1 WHERE object_hash='" + hash + "'");
                statement.executeUpdate("INSERT INTO physical_media_refs (source_kind, source_id, object_hash) VALUES ('POST_IMAGE', '1', '" + hash + "')");
                assertThatThrownBy(() -> statement.executeUpdate("INSERT INTO physical_media_refs (source_kind, source_id, object_hash) VALUES ('POST_IMAGE', '1', '" + hash + "')"))
                        .isInstanceOf(SQLException.class);
                statement.executeUpdate("DELETE FROM physical_media_refs WHERE source_kind='POST_IMAGE' AND source_id='1'");
                statement.executeUpdate("UPDATE physical_media_objects SET state='DELETE_PENDING', deletion_requested=" + trueLiteral);
                statement.executeUpdate("UPDATE physical_media_objects SET state='DELETE_CLAIMED', claim_token='same', claim_generation=1, lease_until=TIMESTAMP '2026-01-01 00:10:00'");
                statement.executeUpdate("UPDATE physical_media_objects SET state='DELETE_PENDING', claim_token=NULL, lease_until=NULL, first_absent_at=TIMESTAMP '2026-01-01 00:00:00', first_absence_generation=1, retry_at=TIMESTAMP '2026-01-01 00:01:00'");
                assertThatThrownBy(() -> statement.executeUpdate("UPDATE physical_media_objects SET state='DELETE_CLAIMED', claim_token='same', lease_until=TIMESTAMP '2026-01-01 00:10:00', retry_at=NULL"))
                        .isInstanceOf(SQLException.class);
                statement.executeUpdate("UPDATE physical_media_objects SET state='DELETE_CLAIMED', claim_token='same', claim_generation=2, lease_until=TIMESTAMP '2026-01-01 00:10:00', retry_at=NULL");
                assertThatThrownBy(() -> statement.executeUpdate("UPDATE physical_media_objects SET state='DELETED', claim_token=NULL, lease_until=NULL, second_absent_at=TIMESTAMP '2026-01-01 00:00:00'"))
                        .isInstanceOf(SQLException.class);
                statement.executeUpdate("UPDATE physical_media_objects SET state='DELETED', claim_token=NULL, lease_until=NULL, second_absent_at=TIMESTAMP '2026-01-01 00:02:00'");
            }
        }
    }
}
