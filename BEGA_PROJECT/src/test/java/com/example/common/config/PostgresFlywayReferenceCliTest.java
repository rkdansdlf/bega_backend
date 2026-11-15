package com.example.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Target-pinned PostgreSQL Flyway reference CLI tests")
class PostgresFlywayReferenceCliTest {

    private static final byte[] BASELINE_SQL = """
            CREATE TABLE parties (id bigint PRIMARY KEY);
            CREATE TABLE party_applications (
                id bigint PRIMARY KEY,
                party_id bigint NOT NULL REFERENCES parties(id)
            );
            """.getBytes(StandardCharsets.UTF_8);
    private static final byte[] OWNERSHIP_TSV = """
            object_type\tobject_name\towner_category
            relation\tpublic.parties\tbackend_postgresql_baseline
            relation\tpublic.party_applications\tbackend_postgresql_baseline
            """.getBytes(StandardCharsets.UTF_8);

    private Map<String, String> validEnvironment() {
        Map<String, String> environment = new HashMap<>();
        environment.put("APP_ENV", "local");
        environment.put("BEGA_DISABLE_DOTENV", "true");
        environment.put("BEGA_DB_RECOVERY_MODE", "reference");
        environment.put("BEGA_REFERENCE_RUN_ID", "20260902r1");
        environment.put(
                "POSTGRES_REFERENCE_URL",
                "jdbc:postgresql://127.0.0.1:55432/bega_v61_reference_20260902r1");
        environment.put("POSTGRES_REFERENCE_USERNAME", "bega_reference_admin");
        environment.put("POSTGRES_REFERENCE_PASSWORD", "local-only-password");
        environment.put(
                "POSTGRES_REFERENCE_LOCATIONS",
                PostgresFlywayReferenceCli.LOCATION);
        environment.put("POSTGRES_REFERENCE_BASELINE_ID", "pre_v61_approved_20260902");
        return environment;
    }

    private PostgresFlywayReferenceCli.Settings settings(String action, String target) {
        return PostgresFlywayReferenceCli.settings(
                validEnvironment(),
                new String[] {action, "--target=" + target});
    }

    private Properties acceptedManifest(byte[] sql) {
        Properties properties = new Properties();
        properties.setProperty("status", "AUTHORITATIVE_BASELINE_ACCEPTED");
        properties.setProperty("baseline.version", "1");
        properties.setProperty("baseline.description", "<< Flyway Baseline >>");
        properties.setProperty("artifact.sha256", PostgresFlywayReferenceCli.sha256(sql));
        properties.setProperty(
                "ownership.matrix.sha256",
                PostgresFlywayReferenceCli.sha256(OWNERSHIP_TSV));
        properties.setProperty("schema.fingerprint.version", "pg-catalog-v1");
        properties.setProperty("schema.fingerprint.sha256", "a".repeat(64));
        properties.setProperty("source.type", "operator_provided_schema_archive");
        properties.setProperty("source.owner", "database-operations");
        properties.setProperty("source.timestamp", "2026-08-08T16:27:34+09:00");
        properties.setProperty("source.commit", "historical-operator-artifact");
        properties.setProperty("extraction.method", "pg_dump --schema-only");
        properties.setProperty("reviewer.operator.approval", "approved-ticket-123");
        properties.setProperty("schema.only", "true");
        properties.setProperty("contains.business.data", "false");
        properties.setProperty("contains.credentials", "false");
        properties.setProperty("contains.acl", "false");
        properties.setProperty("required.object.closure.complete", "true");
        properties.setProperty("unknown.owner.count", "0");
        properties.setProperty("ownership.overlap.count", "0");
        properties.setProperty("derived.only.from.current.source", "false");
        properties.setProperty("party_applications.payment_key", "false");
        properties.setProperty("party_applications.order_id", "false");
        return properties;
    }

    private PostgresFlywayReferenceCli.BaselineManifest acceptedBaseline() {
        return PostgresFlywayReferenceCli.BaselineManifest.parse(
                acceptedManifest(BASELINE_SQL),
                BASELINE_SQL,
                OWNERSHIP_TSV);
    }

    @Test
    @DisplayName("accepts only explicit baseline, migrate, info, or validate actions at target 60 or 61")
    void acceptsOnlyApprovedActionsAndTargets() {
        for (String action : List.of("baseline", "migrate", "info", "validate")) {
            for (String target : List.of("60", "61")) {
                PostgresFlywayReferenceCli.Settings settings = settings(action, target);

                assertThat(settings.action().command()).isEqualTo(action);
                assertThat(settings.target()).isEqualTo(target);
                assertThat(settings.databaseName())
                        .isEqualTo("bega_v61_reference_20260902r1");
            }
        }
    }

    @Test
    @DisplayName("rejects omitted, symbolic, optional, future, and pre-baseline targets")
    void rejectsEveryUnapprovedTarget() {
        assertThatThrownBy(() -> PostgresFlywayReferenceCli.settings(
                validEnvironment(), new String[] {"migrate"}))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("explicit --target");

        for (String target : List.of(
                "", "latest", "next", "current", "60?", "61?", "59", "62", "181", "1")) {
            assertThatThrownBy(() -> PostgresFlywayReferenceCli.settings(
                    validEnvironment(), new String[] {"migrate", "--target=" + target}))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("60 or 61");
        }
    }

    @Test
    @DisplayName("rejects commands outside the bounded reference surface")
    void rejectsForbiddenActionsAndExtraArguments() {
        for (String action : List.of("clean", "repair", "undo", "latest", "bootRun")) {
            assertThatThrownBy(() -> PostgresFlywayReferenceCli.settings(
                    validEnvironment(), new String[] {action, "--target=60"}))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("action");
        }

        assertThatThrownBy(() -> PostgresFlywayReferenceCli.settings(
                validEnvironment(),
                new String[] {"migrate", "--target=60", "--extra"}))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exactly");
    }

    @Test
    @DisplayName("requires local reference mode, dotenv isolation, loopback, and generated DB ownership")
    void rejectsRemoteSourceAndMismatchedReferenceDatabases() {
        Map<String, String> remote = validEnvironment();
        remote.put(
                "POSTGRES_REFERENCE_URL",
                "jdbc:postgresql://prod-db.example:5432/bega_v61_reference_20260902r1");
        assertThatThrownBy(() -> PostgresFlywayReferenceCli.settings(
                remote, new String[] {"migrate", "--target=60"}))
                .hasMessageContaining("loopback");

        for (String database : List.of(
                "kbo_validate", "bega_v61_recovery_20260901b", "postgres", "bega_backend")) {
            Map<String, String> source = validEnvironment();
            source.put(
                    "POSTGRES_REFERENCE_URL",
                    "jdbc:postgresql://127.0.0.1:55432/" + database);
            assertThatThrownBy(() -> PostgresFlywayReferenceCli.settings(
                    source, new String[] {"migrate", "--target=60"}))
                    .hasMessageContaining("generated reference database");
        }

        Map<String, String> otherRun = validEnvironment();
        otherRun.put(
                "POSTGRES_REFERENCE_URL",
                "jdbc:postgresql://127.0.0.1:55432/bega_v61_reference_20260902r2");
        assertThatThrownBy(() -> PostgresFlywayReferenceCli.settings(
                otherRun, new String[] {"migrate", "--target=60"}))
                .hasMessageContaining("current run");

        Map<String, String> production = validEnvironment();
        production.put("APP_ENV", "production");
        assertThatThrownBy(() -> PostgresFlywayReferenceCli.settings(
                production, new String[] {"migrate", "--target=60"}))
                .hasMessageContaining("APP_ENV");
    }

    @Test
    @DisplayName("pins Flyway target and hard safety configuration without connecting")
    void createsOnlyThePinnedFlywayConfiguration() {
        for (String target : List.of("60", "61")) {
            Flyway flyway = PostgresFlywayReferenceCli.configure(
                    settings("migrate", target), acceptedBaseline());

            assertThat(flyway.getConfiguration().getTarget().getVersion()).isEqualTo(target);
            assertThat(flyway.getConfiguration().getLocations())
                    .extracting(Object::toString)
                    .containsExactly(PostgresFlywayReferenceCli.LOCATION);
            assertThat(flyway.getConfiguration().isBaselineOnMigrate()).isFalse();
            assertThat(flyway.getConfiguration().isCleanDisabled()).isTrue();
            assertThat(flyway.getConfiguration().isOutOfOrder()).isFalse();
            assertThat(flyway.getConfiguration().isValidateOnMigrate()).isTrue();
            assertThat(flyway.getConfiguration().isSkipDefaultCallbacks()).isTrue();
            assertThat(flyway.getConfiguration().getBaselineVersion().getVersion()).isEqualTo("1");
            assertThat(flyway.getConfiguration().getBaselineDescription())
                    .isEqualTo("<< Flyway Baseline >>");
        }
    }

    @Test
    @DisplayName("rejects repeatable migrations and SQL callbacks")
    void rejectsUnapprovedRepeatablesAndCallbacks() {
        PostgresFlywayReferenceCli.assertMigrationResourcesApproved(List.of(
                "V36__one.sql", "V60__two.sql", "V61__three.sql"));

        assertThatThrownBy(() -> PostgresFlywayReferenceCli.assertMigrationResourcesApproved(
                List.of("V60__one.sql", "R__refresh_view.sql")))
                .hasMessageContaining("REFERENCE_REPEATABLE_MIGRATION_UNAPPROVED");
        assertThatThrownBy(() -> PostgresFlywayReferenceCli.assertMigrationResourcesApproved(
                List.of("V60__one.sql", "beforeMigrate.sql")))
                .hasMessageContaining("REFERENCE_CALLBACK_UNAPPROVED");
    }

    @Test
    @DisplayName("the current PostgreSQL classpath has only approved versioned resources")
    void currentClasspathContainsNoRepeatablesOrCallbacks() {
        List<String> resources = PostgresFlywayReferenceCli.listMigrationResources(
                getClass().getClassLoader());

        PostgresFlywayReferenceCli.assertMigrationResourcesApproved(resources);
        assertThat(resources).contains(
                "V60__add_party_search_text_and_indexes.sql",
                "V61__add_payment_fields_to_party_applications.sql");
    }

    @Test
    @DisplayName("missing authoritative bundle blocks before a Flyway object can be configured")
    void missingAuthoritativeBundleFailsClosed() {
        assertThatThrownBy(() -> PostgresFlywayReferenceCli.BaselineBundle.load(
                getClass().getClassLoader(), "missing_pre_v61_baseline"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AUTHORITATIVE_BASELINE_ARTIFACT_MISSING");
    }

    @Test
    @DisplayName("accepts only sealed authoritative schema-only baseline manifests")
    void validatesAuthoritativeBaselineManifestAndSql() {
        PostgresFlywayReferenceCli.BaselineManifest manifest = acceptedBaseline();

        assertThat(manifest.baselineVersion()).isEqualTo("1");
        assertThat(manifest.baselineDescription()).isEqualTo("<< Flyway Baseline >>");
        assertThat(manifest.artifactSha256())
                .isEqualTo(PostgresFlywayReferenceCli.sha256(BASELINE_SQL));

        Properties badHash = acceptedManifest(BASELINE_SQL);
        badHash.setProperty("artifact.sha256", "b".repeat(64));
        assertThatThrownBy(() -> PostgresFlywayReferenceCli.BaselineManifest.parse(
                badHash, BASELINE_SQL, OWNERSHIP_TSV))
                .hasMessageContaining("artifact SHA-256");

        Properties derived = acceptedManifest(BASELINE_SQL);
        derived.setProperty("derived.only.from.current.source", "true");
        assertThatThrownBy(() -> PostgresFlywayReferenceCli.BaselineManifest.parse(
                derived, BASELINE_SQL, OWNERSHIP_TSV))
                .hasMessageContaining("current source");

        Properties unknownOwner = acceptedManifest(BASELINE_SQL);
        unknownOwner.setProperty("unknown.owner.count", "1");
        assertThatThrownBy(() -> PostgresFlywayReferenceCli.BaselineManifest.parse(
                unknownOwner, BASELINE_SQL, OWNERSHIP_TSV))
                .hasMessageContaining("unknown owner");

        byte[] dataSql = "INSERT INTO parties(id) VALUES (1);"
                .getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> PostgresFlywayReferenceCli.BaselineManifest.parse(
                acceptedManifest(dataSql), dataSql, OWNERSHIP_TSV))
                .hasMessageContaining("schema-only");

        byte[] aclSql = "GRANT ALL ON parties TO app_user;"
                .getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> PostgresFlywayReferenceCli.BaselineManifest.parse(
                acceptedManifest(aclSql), aclSql, OWNERSHIP_TSV))
                .hasMessageContaining("ACL");

        Properties timestampOrCommit = acceptedManifest(BASELINE_SQL);
        timestampOrCommit.remove("source.timestamp");
        PostgresFlywayReferenceCli.BaselineManifest.parse(
                timestampOrCommit, BASELINE_SQL, OWNERSHIP_TSV);

        byte[] unknownOwnership = """
                object_type\tobject_name\towner_category
                relation\tpublic.parties\tunknown
                relation\tpublic.party_applications\tbackend_postgresql_baseline
                """.getBytes(StandardCharsets.UTF_8);
        Properties unknownOwnershipManifest = acceptedManifest(BASELINE_SQL);
        unknownOwnershipManifest.setProperty(
                "ownership.matrix.sha256",
                PostgresFlywayReferenceCli.sha256(unknownOwnership));
        assertThatThrownBy(() -> PostgresFlywayReferenceCli.BaselineManifest.parse(
                unknownOwnershipManifest, BASELINE_SQL, unknownOwnership))
                .hasMessageContaining("owner category");

        byte[] wrongRequiredOwner = """
                object_type\tobject_name\towner_category
                relation\tpublic.parties\tfuture_schema
                relation\tpublic.party_applications\tbackend_postgresql_baseline
                """.getBytes(StandardCharsets.UTF_8);
        Properties wrongRequiredOwnerManifest = acceptedManifest(BASELINE_SQL);
        wrongRequiredOwnerManifest.setProperty(
                "ownership.matrix.sha256",
                PostgresFlywayReferenceCli.sha256(wrongRequiredOwner));
        assertThatThrownBy(() -> PostgresFlywayReferenceCli.BaselineManifest.parse(
                wrongRequiredOwnerManifest, BASELINE_SQL, wrongRequiredOwner))
                .hasMessageContaining("PostgreSQL baseline");
    }

    @Test
    @DisplayName("baseline is authorized only for a matching non-empty pristine pre-V61 schema")
    void validatesBaselineDatabasePreconditions() {
        PostgresFlywayReferenceCli.Settings settings = settings("baseline", "60");
        PostgresFlywayReferenceCli.BaselineManifest manifest = acceptedBaseline();
        PostgresFlywayReferenceCli.DatabaseSnapshot valid = new PostgresFlywayReferenceCli.DatabaseSnapshot(
                settings.databaseName(),
                2,
                0,
                false,
                Set.of("parties", "party_applications"),
                Map.of("id", "bigint", "party_id", "bigint"),
                manifest.schemaFingerprint());

        PostgresFlywayReferenceCli.assertBaselineEligible(settings, manifest, valid);

        assertThatThrownBy(() -> PostgresFlywayReferenceCli.assertBaselineEligible(
                settings, manifest, valid.withSchemaObjectCount(0)))
                .hasMessageContaining("non-empty");
        assertThatThrownBy(() -> PostgresFlywayReferenceCli.assertBaselineEligible(
                settings, manifest, valid.withHistoryTableExists(true)))
                .hasMessageContaining("history");
        assertThatThrownBy(() -> PostgresFlywayReferenceCli.assertBaselineEligible(
                settings, manifest, valid.withNonEmptyTableCount(1)))
                .hasMessageContaining("business rows");
        assertThatThrownBy(() -> PostgresFlywayReferenceCli.assertBaselineEligible(
                settings, manifest, valid.withSchemaFingerprint("b".repeat(64))))
                .hasMessageContaining("fingerprint");
        assertThatThrownBy(() -> PostgresFlywayReferenceCli.assertBaselineEligible(
                settings,
                manifest,
                valid.withPartyApplicationColumns(Map.of(
                        "id", "bigint", "payment_key", "character varying(200)"))))
                .hasMessageContaining("pre-V61");
    }

    @Test
    @DisplayName("V60 and V61 postconditions enforce exact history and payment column shape")
    void validatesPinnedPostMigrationStates() {
        PostgresFlywayReferenceCli.DatabaseSnapshot v60 = new PostgresFlywayReferenceCli.DatabaseSnapshot(
                "bega_v61_reference_20260902r1",
                2,
                0,
                true,
                Set.of("parties", "party_applications"),
                Map.of("id", "bigint"),
                "a".repeat(64));
        PostgresFlywayReferenceCli.assertPostMigration(
                "60", v60, List.of("1", "36", "51.5", "60"));

        PostgresFlywayReferenceCli.DatabaseSnapshot v61 = v60.withPartyApplicationColumns(Map.of(
                "id", "bigint",
                "payment_key", "character varying(200)",
                "order_id", "character varying(100)"));
        PostgresFlywayReferenceCli.assertPostMigration(
                "61", v61, List.of("1", "36", "60", "61"));

        assertThatThrownBy(() -> PostgresFlywayReferenceCli.assertPostMigration(
                "60", v61, List.of("1", "60", "61")))
                .hasMessageContaining("above target");
        assertThatThrownBy(() -> PostgresFlywayReferenceCli.assertPostMigration(
                "61",
                v60.withPartyApplicationColumns(Map.of(
                        "id", "bigint",
                        "payment_key", "text",
                        "order_id", "character varying(100)")),
                List.of("1", "60", "61")))
                .hasMessageContaining("payment_key");
    }

    @Test
    @DisplayName("the second target-pinned migration execution must apply zero changes")
    void requiresIdempotentSecondMigration() {
        PostgresFlywayReferenceCli.MigrationSummary summary =
                PostgresFlywayReferenceCli.requireIdempotent(
                        new PostgresFlywayReferenceCli.MigrationOutcome(19, "60"),
                        new PostgresFlywayReferenceCli.MigrationOutcome(0, "60"),
                        "60");

        assertThat(summary.firstRunApplied()).isEqualTo(19);
        assertThat(summary.secondRunApplied()).isZero();
        assertThat(summary.latestVersion()).isEqualTo("60");

        assertThatThrownBy(() -> PostgresFlywayReferenceCli.requireIdempotent(
                new PostgresFlywayReferenceCli.MigrationOutcome(1, "61"),
                new PostgresFlywayReferenceCli.MigrationOutcome(1, "62"),
                "61"))
                .hasMessageContaining("second");
    }
}
