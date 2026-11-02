package com.example.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

class LegacyPostImageHoldMigrationTest {
    @ParameterizedTest
    @CsvSource({
        "PostgreSQL, db/migration_postgresql/V196__create_legacy_post_image_holds.sql",
        "Oracle, db/migration/V190__create_legacy_post_image_holds.sql"
    })
    void holdsRemainIndependentAndDefaultToUnresolved(String mode, String resource) throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:hold_"
                + UUID.randomUUID().toString().replace("-", "") + ";MODE=" + mode, "sa", "")) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource(resource));
            try (var statement = connection.createStatement()) {
                String flag = mode.equals("Oracle") ? "1" : "TRUE";
                statement.executeUpdate("INSERT INTO legacy_post_image_holds "
                        + "(post_image_id, owner_user_id, post_id, storage_path, logical_bucket, report_protected) "
                        + "VALUES (12, 34, 56, ' /bucket/legacy%2Fimage.webp ', 'synthetic', " + flag + ")");
                try (var rows = statement.executeQuery("SELECT storage_path, status, created_at FROM legacy_post_image_holds WHERE post_image_id=12")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString(1)).isEqualTo(" /bucket/legacy%2Fimage.webp ");
                    assertThat(rows.getString(2)).isEqualTo("UNRESOLVED");
                    assertThat(rows.getTimestamp(3)).isNotNull();
                }
                assertThatThrownBy(() -> statement.executeUpdate("INSERT INTO legacy_post_image_holds "
                        + "(post_image_id, owner_user_id, post_id, storage_path, report_protected) "
                        + "VALUES (12, 34, 56, 'other', " + flag + ")"))
                        .isInstanceOf(SQLException.class);
                assertThatThrownBy(() -> statement.executeUpdate("UPDATE legacy_post_image_holds SET status='DELETED' WHERE post_image_id=12"))
                        .isInstanceOf(SQLException.class);
            }
        }
    }
}
