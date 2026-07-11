package com.example.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class BaseballMigrationSafetyTest {

    @Test
    void gradleSafetyGateIncludesBaseballMigrationResources() throws IOException {
        String buildGradle = Files.readString(Path.of("build.gradle"), StandardCharsets.UTF_8);

        assertThat(buildGradle)
                .contains("src/main/resources/db/migration_baseball")
                .contains("label : 'baseball'");
    }

    @Test
    void packagedBaseballMigrationSkipsIndexesForTablesNotYetInTheReferenceChain() throws IOException {
        String sql = read(Path.of(
                "src/main/resources/db/migration_baseball/V6__add_live_inning_relay_lookup_indexes.sql"));

        assertThat(sql.toLowerCase())
                .contains("to_regclass('game_inning_scores') is not null")
                .contains("to_regclass('game_play_by_play') is not null");
    }

    @Test
    void operatorSqlBuildsIndexesConcurrentlyAndVerificationFailsWhenTheyAreMissing() throws IOException {
        String applySql = read(Path.of("scripts/sql/baseball/apply_live_inning_relay_indexes.sql"));
        String verifySql = read(Path.of("scripts/sql/baseball/verify_live_inning_relay_indexes.sql"));

        assertThat(applySql.toLowerCase())
                .contains("create index concurrently if not exists idx_game_inning_scores_live")
                .contains("create index concurrently if not exists idx_game_play_by_play_live");
        assertThat(verifySql.toLowerCase())
                .contains("raise exception")
                .contains("idx_game_inning_scores_live")
                .contains("idx_game_play_by_play_live");
    }

    private String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
