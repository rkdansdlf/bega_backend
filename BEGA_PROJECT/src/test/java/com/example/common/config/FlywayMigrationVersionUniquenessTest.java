package com.example.common.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayMigrationVersionUniquenessTest {

    private static final List<Path> MIGRATION_DIRECTORIES = List.of(
            Path.of("src/main/resources/db/migration"),
            Path.of("src/main/resources/db/migration_postgresql"),
            Path.of("src/main/resources/db/migration_baseball"));

    private static final Set<Set<String>> ALLOWED_DUPLICATE_MIGRATION_CONTENT = Set.of(
            Set.of(
                    "src/main/resources/db/migration/V168__canonicalize_cheer_linked_attribution.sql",
                    "src/main/resources/db/migration_postgresql/V174__canonicalize_cheer_linked_attribution.sql"),
            Set.of(
                    "src/main/resources/db/migration/V138__prefix_existing_bcrypt_password_hashes.sql",
                    "src/main/resources/db/migration_postgresql/V138__prefix_existing_bcrypt_password_hashes.sql"),
            Set.of(
                    "src/main/resources/db/migration/V12__force_fix_scrambled_data.sql",
                    "src/main/resources/db/migration/V11__correct_team_franchise_scramble.sql"),
            Set.of(
                    "src/main/resources/db/migration/V134__add_mate_contract_fields.sql",
                    "src/main/resources/db/migration_postgresql/V134__add_mate_contract_fields.sql"),
            Set.of(
                    "src/main/resources/db/migration/V131__ensure_profile_feed_image_url_on_users.sql",
                    "src/main/resources/db/migration/V128__add_profile_feed_image_url_to_users.sql"),
            Set.of(
                    "src/main/resources/db/migration_postgresql/V85__sync_refresh_token_session_metadata.sql",
                    "src/main/resources/db/migration_postgresql/V49__add_refresh_token_session_metadata.sql"),
            Set.of(
                    "src/main/resources/db/migration_postgresql/V131__ensure_profile_feed_image_url_on_users.sql",
                    "src/main/resources/db/migration_postgresql/V128__add_profile_feed_image_url_to_users.sql"));

    @Test
    @DisplayName("primary migration versions are unique")
    void primaryMigrationVersionsAreUnique() throws IOException {
        assertUniqueVersions(Path.of("src/main/resources/db/migration"));
    }

    @Test
    @DisplayName("postgres migration versions are unique")
    void postgresMigrationVersionsAreUnique() throws IOException {
        assertUniqueVersions(Path.of("src/main/resources/db/migration_postgresql"));
    }

    @Test
    @DisplayName("baseball migration versions are unique")
    void baseballMigrationVersionsAreUnique() throws IOException {
        assertUniqueVersions(Path.of("src/main/resources/db/migration_baseball"));
    }

    @Test
    @DisplayName("new duplicate migration SQL content requires explicit allowlist")
    void duplicateMigrationContentRequiresExplicitAllowlist() throws IOException {
        Map<String, List<String>> byContentDigest = new LinkedHashMap<>();

        for (Path migrationDirectory : MIGRATION_DIRECTORIES) {
            for (Path migrationFile : listMigrationFiles(migrationDirectory)) {
                String digest = sha256(normalizeMigrationSql(migrationFile));
                byContentDigest
                        .computeIfAbsent(digest, ignored -> new ArrayList<>())
                        .add(relativeMigrationPath(migrationFile));
            }
        }

        Map<String, List<String>> unexpectedDuplicates = new LinkedHashMap<>();
        Set<Set<String>> observedDuplicateGroups = new HashSet<>();
        byContentDigest.forEach((digest, files) -> {
            if (files.size() <= 1) {
                return;
            }

            Set<String> group = Set.copyOf(files);
            observedDuplicateGroups.add(group);
            if (!ALLOWED_DUPLICATE_MIGRATION_CONTENT.contains(group)) {
                unexpectedDuplicates.put(digest, files);
            }
        });

        assertThat(unexpectedDuplicates)
                .as("Duplicate Flyway migration SQL content outside the existing allowlist")
                .isEmpty();
        assertThat(observedDuplicateGroups)
                .as("Allowed duplicate migration content should stay explicit and current")
                .containsExactlyInAnyOrderElementsOf(ALLOWED_DUPLICATE_MIGRATION_CONTENT);
    }

    @Test
    @DisplayName("users.handle availability indexes are guarded on primary and PostgreSQL migrations")
    void usersHandleAvailabilityIndexesAreGuarded() throws IOException {
        assertHandleAvailabilityMigration(
                Path.of("src/main/resources/db/migration/V140__ensure_users_handle_availability_index.sql"),
                "lower(trim(handle))",
                "create unique index uq_users_handle on users (handle)");
        assertHandleAvailabilityMigration(
                Path.of("src/main/resources/db/migration_postgresql/V140__ensure_users_handle_availability_index.sql"),
                "lower(btrim(handle))",
                "create unique index uq_users_handle on users (handle)");
    }

    private void assertUniqueVersions(Path migrationDirectory) throws IOException {
        Map<String, List<String>> byVersion = new LinkedHashMap<>();
        List<String> malformedFiles = new ArrayList<>();

        try (Stream<Path> paths = Files.list(migrationDirectory)) {
            paths.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".sql"))
                    .sorted()
                    .forEach(name -> {
                        int delimiterIndex = name.indexOf("__");
                        if (!name.startsWith("V") || delimiterIndex <= 1) {
                            malformedFiles.add(name);
                            return;
                        }

                        String version = name.substring(1, delimiterIndex);
                        byVersion.computeIfAbsent(version, ignored -> new ArrayList<>()).add(name);
                    });
        }

        assertThat(malformedFiles)
                .as("Malformed Flyway migration filenames in %s", migrationDirectory)
                .isEmpty();

        Map<String, List<String>> duplicates = byVersion.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .collect(LinkedHashMap::new, (map, entry) -> map.put(entry.getKey(), entry.getValue()), Map::putAll);

        assertThat(duplicates)
                .as("Duplicate Flyway migration versions in %s", migrationDirectory)
                .isEmpty();
    }

    private List<Path> listMigrationFiles(Path migrationDirectory) throws IOException {
        try (Stream<Path> paths = Files.list(migrationDirectory)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .sorted()
                    .toList();
        }
    }

    private String normalizeMigrationSql(Path migrationFile) throws IOException {
        return Files.readString(migrationFile)
                .replaceAll("(?s)/\\*.*?\\*/", " ")
                .replaceAll("(?m)--.*$", " ")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest is unavailable", e);
        }
    }

    private String relativeMigrationPath(Path migrationFile) {
        return migrationFile.toString().replace('\\', '/');
    }

    private void assertHandleAvailabilityMigration(
            Path migrationFile,
            String collisionPrecheck,
            String uniqueIndexStatement) throws IOException {
        String normalizedSql = Files.readString(migrationFile)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");

        assertThat(normalizedSql)
                .as("%s should fail fast on normalized handle collisions", migrationFile)
                .contains(collisionPrecheck);
        assertThat(normalizedSql)
                .as("%s should create or verify a unique users(handle) lookup", migrationFile)
                .contains(uniqueIndexStatement);
        assertThat(normalizedSql)
                .as("%s must not reintroduce the public check-email surface", migrationFile)
                .doesNotContain("check-email");
    }

    @Test
    @DisplayName("Oracle game events live lookup migration no-ops when table is absent")
    void oracleGameEventsLiveLookupMigrationNoOpsWhenTableIsAbsent() throws IOException {
        Path migrationFile = Path.of("src/main/resources/db/migration/V143__add_game_events_live_lookup_index.sql");
        String normalizedSql = Files.readString(migrationFile)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");

        assertThat(normalizedSql)
                .as("%s should inspect user_tables before indexing optional game_events", migrationFile)
                .contains("from user_tables")
                .contains("table_name = 'game_events'");
        assertThat(normalizedSql.indexOf("from user_tables"))
                .as("%s should check table existence before checking indexes", migrationFile)
                .isLessThan(normalizedSql.indexOf("from user_indexes"));
        assertThat(normalizedSql)
                .as("%s should only create the live lookup index after confirming game_events exists", migrationFile)
                .contains("if v_table_count > 0 then")
                .contains("create index idx_game_events_live_lookup on game_events(game_id, event_seq)");
        assertThat(normalizedSql.indexOf("if v_table_count > 0 then"))
                .as("%s should put the create index statement inside the table-exists branch", migrationFile)
                .isLessThan(normalizedSql.indexOf("create index idx_game_events_live_lookup"));
    }

    @Test
    @DisplayName("Oracle scheduled diary migration only relaxes winning when it is not nullable")
    void oracleScheduledDiaryMigrationOnlyRelaxesWinningWhenNotNullable() throws IOException {
        Path migrationFile = Path.of("src/main/resources/db/migration/V149__support_scheduled_diary_records.sql");
        String normalizedSql = Files.readString(migrationFile)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");

        assertThat(normalizedSql)
                .as("%s should inspect the current nullable flag before modifying winning", migrationFile)
                .contains("from user_tab_cols")
                .contains("column_name = 'winning'")
                .contains("nullable = 'n'");
        assertThat(normalizedSql.indexOf("nullable = 'n'"))
                .as("%s should check non-nullability before altering winning", migrationFile)
                .isLessThan(normalizedSql.indexOf("alter table bega_diary modify (winning null)"));
    }

    @Test
    @DisplayName("Oracle canonical game range migration ignores equivalent existing index")
    void oracleCanonicalGameRangeMigrationIgnoresEquivalentExistingIndex() throws IOException {
        Path migrationFile = Path.of("src/main/resources/db/migration/V152__ensure_game_range_canonical_active_index.sql");
        String normalizedSql = Files.readString(migrationFile)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");

        assertThat(normalizedSql)
                .as("%s should ignore Oracle ORA-01408 when equivalent columns are already indexed", migrationFile)
                .contains("pragma exception_init(e_columns_already_indexed, -1408)")
                .contains("when e_columns_already_indexed then null");
        assertThat(normalizedSql.indexOf("execute immediate"))
                .as("%s should handle equivalent-index errors around the create index statement", migrationFile)
                .isLessThan(normalizedSql.indexOf("when e_columns_already_indexed then null"));
    }

    @Test
    @DisplayName("home/auth DB bottleneck indexes are guarded on primary and PostgreSQL migrations")
    void homeAuthDbBottleneckIndexesAreGuarded() throws IOException {
        assertBottleneckIndexMigration(
                Path.of("src/main/resources/db/migration/V150__add_home_auth_bottleneck_indexes.sql"),
                "user_ind_columns",
                "upper(game_status)",
                "idx_game_home_scheduled_window",
                "idx_refresh_tokens_token_lookup",
                "idx_refresh_tokens_email_lookup",
                "idx_users_email_lookup",
                "idx_game_date_lookup");
        assertBottleneckIndexMigration(
                Path.of("src/main/resources/db/migration_postgresql/V155__add_home_auth_bottleneck_indexes.sql"),
                "pg_index",
                "upper(game_status)",
                "idx_game_home_scheduled_window",
                "idx_refresh_tokens_token_lookup",
                "idx_refresh_tokens_email_lookup",
                "idx_users_email_lookup",
                "idx_game_date_lookup");
    }

    private void assertBottleneckIndexMigration(Path migrationFile, String indexCatalog, String... requiredSql)
            throws IOException {
        assertThat(migrationFile)
                .as("%s should exist", migrationFile)
                .exists();

        String normalizedSql = Files.readString(migrationFile)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");

        assertThat(normalizedSql)
                .as("%s should inspect existing indexes before adding drift-repair indexes", migrationFile)
                .contains(indexCatalog);
        assertThat(normalizedSql)
                .as("%s should be scoped to home/auth slow-query tables", migrationFile)
                .contains("game")
                .contains("refresh_tokens")
                .contains("users");
        assertThat(normalizedSql)
                .as("%s should define all required bottleneck lookup indexes", migrationFile)
                .contains(requiredSql);
    }

    @Test
    @DisplayName("payment compensation lookup indexes are guarded on primary and PostgreSQL migrations")
    void paymentCompensationIndexesAreGuarded() throws IOException {
        assertPaymentCompensationIndexMigration(
                Path.of("src/main/resources/db/migration/V151__add_payment_compensation_lookup_index.sql"),
                "user_ind_columns",
                "idx_payment_intents_status_updated_at",
                "status",
                "updated_at");
        assertPaymentCompensationIndexMigration(
                Path.of("src/main/resources/db/migration_postgresql/V156__add_payment_compensation_lookup_index.sql"),
                "pg_indexes",
                "idx_payment_intents_status_updated_at",
                "status, updated_at");
    }

    private void assertPaymentCompensationIndexMigration(Path migrationFile, String indexCatalog, String... requiredSql)
            throws IOException {
        assertThat(migrationFile)
                .as("%s should exist", migrationFile)
                .exists();

        String normalizedSql = Files.readString(migrationFile)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");

        assertThat(normalizedSql)
                .as("%s should inspect existing indexes before adding payment compensation lookup indexes", migrationFile)
                .contains(indexCatalog);
        assertThat(normalizedSql)
                .as("%s should be scoped to payment intent compensation lookups", migrationFile)
                .contains("payment_intents");
        assertThat(normalizedSql)
                .as("%s should define the required payment compensation lookup index", migrationFile)
                .contains(requiredSql);
    }

    @Test
    @DisplayName("canonical game range indexes are drift-repaired on primary and PostgreSQL migrations")
    void canonicalGameRangeIndexesAreDriftRepaired() throws IOException {
        assertCanonicalGameRangeMigration(
                Path.of("src/main/resources/db/migration/V152__ensure_game_range_canonical_active_index.sql"),
                "user_ind_columns",
                "idx_game_range_canonical_active",
                "game_date",
                "home_team",
                "away_team",
                "game_id");
        assertCanonicalGameRangeMigration(
                Path.of("src/main/resources/db/migration_postgresql/V157__ensure_game_range_canonical_active_index.sql"),
                "pg_indexes",
                "idx_game_range_canonical_active",
                "game_date, home_team, away_team, game_id",
                "is_dummy is not true",
                "game_id not like");
    }

    private void assertCanonicalGameRangeMigration(Path migrationFile, String indexCatalog, String... requiredSql)
            throws IOException {
        assertThat(migrationFile)
                .as("%s should exist", migrationFile)
                .exists();

        String normalizedSql = Files.readString(migrationFile)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");

        assertThat(normalizedSql)
                .as("%s should inspect existing indexes before adding canonical range lookup indexes", migrationFile)
                .contains(indexCatalog);
        assertThat(normalizedSql)
                .as("%s should be scoped to game canonical range lookups", migrationFile)
                .contains("game");
        assertThat(normalizedSql)
                .as("%s should define the required canonical game range lookup index", migrationFile)
                .contains(requiredSql);
    }
}
