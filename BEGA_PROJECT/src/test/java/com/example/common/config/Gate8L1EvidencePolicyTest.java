package com.example.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Gate 8L-1 evidence policy tests")
class Gate8L1EvidencePolicyTest {

    private static final String ALLOWLIST_RESOURCE =
            "/db/gate8l1-warning-allowlist.tsv";
    private static final String OBSERVED_WARNINGS_RESOURCE =
            "/db/gate8l1-observed-warnings.txt";
    private static final Path POSTGRES_MIGRATIONS =
            Path.of("src/main/resources/db/migration_postgresql");

    @Test
    @DisplayName("allows exactly the 14 sealed compatibility notices and maps every notice to source DDL")
    void warningAllowlistMatchesSealedEvidenceAndMigrationSources() throws IOException {
        List<WarningRule> rules = loadRules();
        List<String> observedWarnings = readResourceLines(OBSERVED_WARNINGS_RESOURCE);

        assertThat(rules).hasSize(14);
        assertThat(observedWarnings).containsExactlyElementsOf(
                rules.stream().map(WarningRule::noticeSignature).toList());
        assertThat(new HashSet<>(observedWarnings)).hasSize(14);

        for (WarningRule rule : rules) {
            assertThat(rule.noticeSignature())
                    .doesNotContain("*")
                    .contains("already exists, skipping")
                    .contains("SQL State:");
            assertThat(rule.script())
                    .startsWith("V" + rule.version().replace('.', '_') + "__")
                    .endsWith(".sql");

            Path guardedScript = POSTGRES_MIGRATIONS.resolve(rule.script());
            Path predecessorScript = POSTGRES_MIGRATIONS.resolve(rule.predecessorScript());
            assertThat(guardedScript).exists().isRegularFile();
            assertThat(predecessorScript).exists().isRegularFile();

            String guardedSql = Files.readString(guardedScript, StandardCharsets.UTF_8);
            String predecessorSql = Files.readString(predecessorScript, StandardCharsets.UTF_8);
            assertThat(guardedSql).contains(rule.guardMarker());
            assertThat(predecessorSql).contains(rule.predecessorMarker());

            if (guardedScript.equals(predecessorScript)) {
                assertThat(guardedSql.indexOf(rule.predecessorMarker()))
                        .isLessThan(guardedSql.indexOf(rule.guardMarker()));
            }
        }
    }

    @Test
    @DisplayName("accepts a second-run no-op only from numeric version and stable catalog evidence")
    void noOpEvidenceUsesCatalogStateInsteadOfLogText() {
        NoOpEvidence evidence = new NoOpEvidence(
                "181",
                0,
                null,
                "181",
                List.of("1:52", "140:181"),
                List.of("1:52", "140:181"),
                "sha256:stable-schema",
                "sha256:stable-schema");

        assertNoOp(evidence);

        assertThat(MigrationVersion.fromVersion("120.5"))
                .isGreaterThan(MigrationVersion.fromVersion("99.5"));
        assertThat(MigrationVersion.fromVersion("181"))
                .isGreaterThan(MigrationVersion.fromVersion("99"));
    }

    @Test
    @DisplayName("rejects applied migrations, history drift, schema drift, and version drift")
    void noOpEvidenceFailsClosedOnCatalogDrift() {
        NoOpEvidence baseline = new NoOpEvidence(
                "181",
                0,
                null,
                "181",
                List.of("1:52", "140:181"),
                List.of("1:52", "140:181"),
                "sha256:stable-schema",
                "sha256:stable-schema");

        assertThatThrownBy(() -> assertNoOp(baseline.withSecondRunApplied(1)))
                .hasMessageContaining("applied");
        assertThatThrownBy(() -> assertNoOp(baseline.withHistoryAfter(List.of("1:52"))))
                .hasMessageContaining("history");
        assertThatThrownBy(() -> assertNoOp(baseline.withSchemaFingerprintAfter("sha256:changed")))
                .hasMessageContaining("fingerprint");
        assertThatThrownBy(() -> assertNoOp(baseline.withCatalogLatestVersion("180")))
                .hasMessageContaining("version");
    }

    private static void assertNoOp(NoOpEvidence evidence) {
        if (evidence.secondRunApplied() != 0) {
            throw new IllegalStateException("second Flyway run applied migrations");
        }
        String actualLatest = evidence.secondRunTargetVersion() != null
                ? evidence.secondRunTargetVersion()
                : evidence.catalogLatestVersion();
        MigrationVersion expected = MigrationVersion.fromVersion(evidence.expectedLatestVersion());
        MigrationVersion actual = MigrationVersion.fromVersion(actualLatest);
        if (!expected.equals(actual)) {
            throw new IllegalStateException("latest numeric Flyway version changed");
        }
        if (!evidence.historyBefore().equals(evidence.historyAfter())) {
            throw new IllegalStateException("Flyway history changed");
        }
        if (!evidence.schemaFingerprintBefore().equals(evidence.schemaFingerprintAfter())) {
            throw new IllegalStateException("schema fingerprint changed");
        }
    }

    private static List<WarningRule> loadRules() throws IOException {
        List<String> lines = readResourceLines(ALLOWLIST_RESOURCE);
        assertThat(lines).isNotEmpty();
        assertThat(lines.getFirst()).isEqualTo(
                "version\tscript\tobject\tnotice_signature\tpredecessor_script\tguard_marker"
                        + "\tpredecessor_marker\trationale");

        List<WarningRule> rules = new ArrayList<>();
        Set<String> signatures = new HashSet<>();
        for (String line : lines.subList(1, lines.size())) {
            String[] columns = line.split("\\t", -1);
            assertThat(columns).hasSize(8);
            WarningRule rule = new WarningRule(
                    columns[0], columns[1], columns[2], columns[3],
                    columns[4], columns[5], columns[6], columns[7]);
            assertThat(rule.objectName()).isNotBlank();
            assertThat(rule.rationale()).isNotBlank();
            assertThat(signatures.add(rule.noticeSignature())).isTrue();
            rules.add(rule);
        }
        return rules;
    }

    private static List<String> readResourceLines(String resource) throws IOException {
        try (InputStream input = Gate8L1EvidencePolicyTest.class.getResourceAsStream(resource)) {
            assertThat(input).as(resource).isNotNull();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(input, StandardCharsets.UTF_8))) {
                return reader.lines().filter(line -> !line.isBlank()).toList();
            }
        }
    }

    private record WarningRule(
            String version,
            String script,
            String objectName,
            String noticeSignature,
            String predecessorScript,
            String guardMarker,
            String predecessorMarker,
            String rationale) {
    }

    private record NoOpEvidence(
            String expectedLatestVersion,
            int secondRunApplied,
            String secondRunTargetVersion,
            String catalogLatestVersion,
            List<String> historyBefore,
            List<String> historyAfter,
            String schemaFingerprintBefore,
            String schemaFingerprintAfter) {

        NoOpEvidence withSecondRunApplied(int value) {
            return new NoOpEvidence(
                    expectedLatestVersion, value, secondRunTargetVersion, catalogLatestVersion,
                    historyBefore, historyAfter, schemaFingerprintBefore, schemaFingerprintAfter);
        }

        NoOpEvidence withCatalogLatestVersion(String value) {
            return new NoOpEvidence(
                    expectedLatestVersion, secondRunApplied, secondRunTargetVersion, value,
                    historyBefore, historyAfter, schemaFingerprintBefore, schemaFingerprintAfter);
        }

        NoOpEvidence withHistoryAfter(List<String> value) {
            return new NoOpEvidence(
                    expectedLatestVersion, secondRunApplied, secondRunTargetVersion,
                    catalogLatestVersion, historyBefore, value,
                    schemaFingerprintBefore, schemaFingerprintAfter);
        }

        NoOpEvidence withSchemaFingerprintAfter(String value) {
            return new NoOpEvidence(
                    expectedLatestVersion, secondRunApplied, secondRunTargetVersion,
                    catalogLatestVersion, historyBefore, historyAfter,
                    schemaFingerprintBefore, value);
        }
    }
}
