package com.example.cheerboard;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class CheerLikeCountMigrationSqlTest {

    @Test
    @DisplayName("PostgreSQL migration backfills cheer_post.likecount from cheer_post_like rows")
    void postgresMigrationReconcilesLikeCounts() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:cheer_like_migration;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "sa",
                "")) {
            execute(connection, "CREATE TABLE cheer_post (id BIGINT PRIMARY KEY, likecount INTEGER NOT NULL DEFAULT 0)");
            execute(connection, "CREATE TABLE cheer_post_like (post_id BIGINT NOT NULL, user_id BIGINT NOT NULL)");
            execute(connection, "INSERT INTO cheer_post(id, likecount) VALUES (211, 2), (212, 6), (213, 0)");
            execute(connection, "INSERT INTO cheer_post_like(post_id, user_id) VALUES (213, 15)");

            executeStatements(connection, loadSql("db/migration_postgresql/V129__reconcile_cheer_post_like_counts.sql"));

            assertThat(readLikeCounts(connection))
                    .containsEntry(211L, 0)
                    .containsEntry(212L, 0)
                    .containsEntry(213L, 1);
        }
    }

    private void executeStatements(Connection connection, String sql) throws SQLException {
        for (String statement : sql.split(";")) {
            String trimmed = statement.trim();
            if (!trimmed.isEmpty()) {
                execute(connection, trimmed);
            }
        }
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private Map<Long, Integer> readLikeCounts(Connection connection) throws SQLException {
        Map<Long, Integer> counts = new HashMap<>();
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT id, likecount FROM cheer_post ORDER BY id")) {
            while (resultSet.next()) {
                counts.put(resultSet.getLong("id"), resultSet.getInt("likecount"));
            }
        }
        return counts;
    }

    private String loadSql(String resourcePath) throws IOException {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        try (var inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
