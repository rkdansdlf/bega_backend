package com.example.common.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayMigrationVersionUniquenessTest {

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
}
