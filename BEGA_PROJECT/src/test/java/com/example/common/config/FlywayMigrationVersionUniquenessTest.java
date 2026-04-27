package com.example.common.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
}
