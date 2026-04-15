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

class CheerOrphanPostCleanupMigrationSqlTest {

    @Test
    @DisplayName("PostgreSQL migration deletes orphaned cheer posts and preserves surviving repost rows")
    void postgresMigrationDeletesOrphanedCheerPosts() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:cheer_orphan_post_cleanup;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "sa",
                "")) {
            execute(connection, "CREATE TABLE users (id BIGINT PRIMARY KEY)");
            execute(connection, """
                    CREATE TABLE cheer_post (
                        id BIGINT PRIMARY KEY,
                        author_id BIGINT NOT NULL,
                        repost_of_id BIGINT,
                        deleted BOOLEAN NOT NULL DEFAULT FALSE,
                        CONSTRAINT fk_cheer_post_repost_of_id
                            FOREIGN KEY (repost_of_id)
                            REFERENCES cheer_post (id)
                            ON DELETE SET NULL
                    )
                    """);
            execute(connection, """
                    CREATE TABLE cheer_comment (
                        id BIGINT PRIMARY KEY,
                        post_id BIGINT NOT NULL,
                        CONSTRAINT fk_cheer_comment_post_id
                            FOREIGN KEY (post_id)
                            REFERENCES cheer_post (id)
                            ON DELETE CASCADE
                    )
                    """);
            execute(connection, """
                    CREATE TABLE cheer_comment_like (
                        comment_id BIGINT NOT NULL,
                        user_id BIGINT NOT NULL,
                        PRIMARY KEY (comment_id, user_id),
                        CONSTRAINT fk_cheer_comment_like_comment_id
                            FOREIGN KEY (comment_id)
                            REFERENCES cheer_comment (id)
                            ON DELETE CASCADE
                    )
                    """);
            execute(connection, """
                    CREATE TABLE cheer_post_like (
                        post_id BIGINT NOT NULL,
                        user_id BIGINT NOT NULL,
                        PRIMARY KEY (post_id, user_id),
                        CONSTRAINT fk_cheer_post_like_post_id
                            FOREIGN KEY (post_id)
                            REFERENCES cheer_post (id)
                            ON DELETE CASCADE
                    )
                    """);
            execute(connection, """
                    CREATE TABLE cheer_post_bookmark (
                        post_id BIGINT NOT NULL,
                        user_id BIGINT NOT NULL,
                        PRIMARY KEY (post_id, user_id),
                        CONSTRAINT fk_cheer_post_bookmark_post_id
                            FOREIGN KEY (post_id)
                            REFERENCES cheer_post (id)
                            ON DELETE CASCADE
                    )
                    """);
            execute(connection, """
                    CREATE TABLE cheer_post_repost (
                        post_id BIGINT NOT NULL,
                        user_id BIGINT NOT NULL,
                        PRIMARY KEY (post_id, user_id),
                        CONSTRAINT fk_cheer_post_repost_post_id
                            FOREIGN KEY (post_id)
                            REFERENCES cheer_post (id)
                            ON DELETE CASCADE
                    )
                    """);
            execute(connection, """
                    CREATE TABLE cheer_post_reports (
                        id BIGINT PRIMARY KEY,
                        post_id BIGINT NOT NULL,
                        CONSTRAINT fk_cheer_post_reports_post_id
                            FOREIGN KEY (post_id)
                            REFERENCES cheer_post (id)
                            ON DELETE CASCADE
                    )
                    """);
            execute(connection, """
                    CREATE TABLE post_images (
                        id BIGINT PRIMARY KEY,
                        post_id BIGINT NOT NULL,
                        CONSTRAINT fk_post_images_post_id
                            FOREIGN KEY (post_id)
                            REFERENCES cheer_post (id)
                            ON DELETE CASCADE
                    )
                    """);

            execute(connection, "INSERT INTO users(id) VALUES (1), (2)");
            execute(connection, """
                    INSERT INTO cheer_post(id, author_id, repost_of_id, deleted) VALUES
                    (169, 80, NULL, FALSE),
                    (170, 80, NULL, FALSE),
                    (171, 1, NULL, FALSE),
                    (172, 2, 169, FALSE)
                    """);
            execute(connection, "INSERT INTO cheer_comment(id, post_id) VALUES (501, 169), (502, 171)");
            execute(connection, "INSERT INTO cheer_comment_like(comment_id, user_id) VALUES (501, 1), (502, 2)");
            execute(connection, "INSERT INTO cheer_post_like(post_id, user_id) VALUES (169, 1), (170, 2), (171, 2)");
            execute(connection, "INSERT INTO cheer_post_bookmark(post_id, user_id) VALUES (169, 1), (171, 1)");
            execute(connection, "INSERT INTO cheer_post_repost(post_id, user_id) VALUES (169, 1), (171, 2)");
            execute(connection, "INSERT INTO cheer_post_reports(id, post_id) VALUES (601, 169), (602, 171)");
            execute(connection, "INSERT INTO post_images(id, post_id) VALUES (701, 169), (702, 171)");

            executeStatements(connection, loadSql("db/migration_postgresql/V130__cleanup_orphaned_cheer_posts.sql"));

            assertThat(readPosts(connection))
                    .containsOnlyKeys(171L, 172L)
                    .containsEntry(171L, null)
                    .containsEntry(172L, null);
            assertThat(countRows(connection, "cheer_comment")).isEqualTo(1);
            assertThat(countRows(connection, "cheer_comment_like")).isEqualTo(1);
            assertThat(countRows(connection, "cheer_post_like")).isEqualTo(1);
            assertThat(countRows(connection, "cheer_post_bookmark")).isEqualTo(1);
            assertThat(countRows(connection, "cheer_post_repost")).isEqualTo(1);
            assertThat(countRows(connection, "cheer_post_reports")).isEqualTo(1);
            assertThat(countRows(connection, "post_images")).isEqualTo(1);
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

    private Map<Long, Long> readPosts(Connection connection) throws SQLException {
        Map<Long, Long> posts = new HashMap<>();
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT id, repost_of_id FROM cheer_post ORDER BY id")) {
            while (resultSet.next()) {
                long id = resultSet.getLong("id");
                Long repostOfId = (Long) resultSet.getObject("repost_of_id");
                posts.put(id, repostOfId);
            }
        }
        return posts;
    }

    private int countRows(Connection connection, String tableName) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    private String loadSql(String resourcePath) throws IOException {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        try (var inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
