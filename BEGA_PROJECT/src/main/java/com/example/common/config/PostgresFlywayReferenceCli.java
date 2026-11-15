package com.example.common.config;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.output.BaselineResult;
import org.flywaydb.core.api.output.MigrateResult;
import org.flywaydb.core.api.output.ValidateResult;

/**
 * Standalone, local-only Flyway runner for a generated historical reference database.
 *
 * <p>This runner deliberately has no Spring application context, database creation,
 * cleanup, repair, worker, scheduler, seed, or web-server surface. It can target only
 * PostgreSQL V60 or V61 and refuses to run until a sealed reference-only baseline
 * bundle is present on the classpath.
 */
public final class PostgresFlywayReferenceCli {

    static final String LOCATION = "classpath:db/migration_postgresql";
    static final String REFERENCE_RESOURCE_ROOT = "db/reference/postgresql";
    static final String FINGERPRINT_VERSION = "pg-catalog-v1";

    private static final String BASELINE_VERSION = "1";
    private static final String BASELINE_DESCRIPTION = "<< Flyway Baseline >>";

    private static final Pattern RUN_ID = Pattern.compile("^[a-z0-9][a-z0-9_]{2,40}$");
    private static final Pattern BASELINE_ID = Pattern.compile("^[a-z0-9][a-z0-9_]{2,80}$");
    private static final Pattern SHA_256 = Pattern.compile("^[a-f0-9]{64}$");
    private static final Pattern LOOPBACK_JDBC_URL = Pattern.compile(
            "^jdbc:postgresql://(127\\.0\\.0\\.1|localhost|\\[::1\\]):([0-9]{1,5})/"
                    + "(bega_v61_reference_([a-z0-9][a-z0-9_]{2,40}))$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern REPEATABLE_MIGRATION = Pattern.compile(
            "^R__.+\\.sql$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SQL_CALLBACK = Pattern.compile(
            "^(before|after).+\\.sql$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DATA_STATEMENT = Pattern.compile(
            "(?im)^\\s*(INSERT|COPY|UPDATE|DELETE|MERGE)\\b");
    private static final Pattern ACL_STATEMENT = Pattern.compile(
            "(?im)^\\s*(GRANT|REVOKE|SET\\s+SESSION\\s+AUTHORIZATION)\\b"
                    + "|(?im)^\\s*ALTER\\b[^;]*\\bOWNER\\s+TO\\b");
    private static final Pattern CREDENTIAL_STATEMENT = Pattern.compile(
            "(?im)^\\s*(CREATE|ALTER)\\s+(USER|ROLE)\\b[^;]*\\bPASSWORD\\b");
    private static final Pattern PARTIES_DDL = Pattern.compile(
            "(?is)\\bCREATE\\s+TABLE(?:\\s+IF\\s+NOT\\s+EXISTS)?\\s+"
                    + "(?:public\\.)?\\\"?parties\\\"?\\b");
    private static final Pattern PARTY_APPLICATIONS_DDL = Pattern.compile(
            "(?is)\\bCREATE\\s+TABLE(?:\\s+IF\\s+NOT\\s+EXISTS)?\\s+"
                    + "(?:public\\.)?\\\"?party_applications\\\"?\\b");
    private static final Set<String> OWNER_CATEGORIES = Set.of(
            "backend_postgresql_baseline",
            "backend_versioned_migration",
            "ai_runtime_infrastructure",
            "other_infrastructure",
            "future_schema",
            "manual_baseball_dependency");

    private static final List<String> FINGERPRINT_QUERIES = List.of(
            """
            SELECT n.nspname, c.relname, c.relkind::text,
                   pg_get_userbyid(c.relowner), COALESCE(obj_description(c.oid, 'pg_class'), '')
            FROM pg_class c
            JOIN pg_namespace n ON n.oid = c.relnamespace
            WHERE n.nspname NOT IN ('pg_catalog', 'information_schema')
              AND n.nspname !~ '^pg_toast'
              AND c.relkind IN ('r', 'p', 'v', 'm', 'S')
              AND c.relname <> 'flyway_schema_history'
            ORDER BY n.nspname, c.relname, c.relkind
            """,
            """
            SELECT n.nspname, c.relname, a.attnum::text, a.attname,
                   pg_catalog.format_type(a.atttypid, a.atttypmod),
                   a.attnotnull::text,
                   COALESCE(pg_get_expr(d.adbin, d.adrelid), ''),
                   a.attidentity::text, a.attgenerated::text
            FROM pg_attribute a
            JOIN pg_class c ON c.oid = a.attrelid
            JOIN pg_namespace n ON n.oid = c.relnamespace
            LEFT JOIN pg_attrdef d ON d.adrelid = a.attrelid AND d.adnum = a.attnum
            WHERE n.nspname NOT IN ('pg_catalog', 'information_schema')
              AND n.nspname !~ '^pg_toast'
              AND c.relkind IN ('r', 'p', 'v', 'm')
              AND c.relname <> 'flyway_schema_history'
              AND a.attnum > 0
              AND NOT a.attisdropped
            ORDER BY n.nspname, c.relname, a.attnum
            """,
            """
            SELECT n.nspname, c.relname, con.conname, con.contype::text,
                   pg_get_constraintdef(con.oid, true)
            FROM pg_constraint con
            JOIN pg_class c ON c.oid = con.conrelid
            JOIN pg_namespace n ON n.oid = c.relnamespace
            WHERE n.nspname NOT IN ('pg_catalog', 'information_schema')
              AND n.nspname !~ '^pg_toast'
              AND c.relname <> 'flyway_schema_history'
            ORDER BY n.nspname, c.relname, con.conname
            """,
            """
            SELECT schemaname, tablename, indexname, indexdef
            FROM pg_indexes
            WHERE schemaname NOT IN ('pg_catalog', 'information_schema')
              AND schemaname !~ '^pg_toast'
              AND tablename <> 'flyway_schema_history'
            ORDER BY schemaname, tablename, indexname
            """,
            """
            SELECT n.nspname, p.proname, pg_get_function_identity_arguments(p.oid),
                   pg_get_function_result(p.oid), pg_get_functiondef(p.oid)
            FROM pg_proc p
            JOIN pg_namespace n ON n.oid = p.pronamespace
            WHERE n.nspname NOT IN ('pg_catalog', 'information_schema')
              AND n.nspname !~ '^pg_toast'
            ORDER BY n.nspname, p.proname, pg_get_function_identity_arguments(p.oid)
            """,
            """
            SELECT n.nspname, c.relname, t.tgname, pg_get_triggerdef(t.oid, true)
            FROM pg_trigger t
            JOIN pg_class c ON c.oid = t.tgrelid
            JOIN pg_namespace n ON n.oid = c.relnamespace
            WHERE NOT t.tgisinternal
              AND n.nspname NOT IN ('pg_catalog', 'information_schema')
              AND n.nspname !~ '^pg_toast'
              AND c.relname <> 'flyway_schema_history'
            ORDER BY n.nspname, c.relname, t.tgname
            """,
            """
            SELECT n.nspname, t.typname, e.enumsortorder::text, e.enumlabel
            FROM pg_type t
            JOIN pg_namespace n ON n.oid = t.typnamespace
            JOIN pg_enum e ON e.enumtypid = t.oid
            WHERE n.nspname NOT IN ('pg_catalog', 'information_schema')
              AND n.nspname !~ '^pg_toast'
            ORDER BY n.nspname, t.typname, e.enumsortorder
            """);

    private PostgresFlywayReferenceCli() {
    }

    static Settings settings(Map<String, String> environment, String[] args) {
        if (args.length != 2) {
            throw new IllegalStateException(
                    "reference action requires exactly one explicit --target: "
                            + "<action> --target=<60|61>");
        }
        Action action = Action.parse(args[0]);
        if (!args[1].startsWith("--target=")) {
            throw new IllegalStateException("an explicit --target=60 or --target=61 is required");
        }
        String target = args[1].substring("--target=".length());
        requireAllowedTarget(target);

        requireExact(environment, "APP_ENV", "local");
        requireExact(environment, "BEGA_DISABLE_DOTENV", "true");
        requireExact(environment, "BEGA_DB_RECOVERY_MODE", "reference");

        String runId = require(environment, "BEGA_REFERENCE_RUN_ID");
        if (!RUN_ID.matcher(runId).matches()) {
            throw new IllegalStateException("BEGA_REFERENCE_RUN_ID is invalid");
        }

        String url = require(environment, "POSTGRES_REFERENCE_URL");
        Matcher urlMatcher = LOOPBACK_JDBC_URL.matcher(url);
        if (!urlMatcher.matches()) {
            if (!url.startsWith("jdbc:postgresql://127.0.0.1:")
                    && !url.startsWith("jdbc:postgresql://localhost:")
                    && !url.startsWith("jdbc:postgresql://[::1]:")) {
                throw new IllegalStateException(
                        "POSTGRES_REFERENCE_URL must target an explicit loopback endpoint");
            }
            throw new IllegalStateException(
                    "POSTGRES_REFERENCE_URL must target a generated reference database");
        }
        int port = Integer.parseInt(urlMatcher.group(2));
        if (port < 1 || port > 65535) {
            throw new IllegalStateException("POSTGRES_REFERENCE_URL port is outside 1-65535");
        }
        assertLoopback(urlMatcher.group(1));

        String databaseName = urlMatcher.group(3);
        String databaseRunId = urlMatcher.group(4);
        if (!runId.equals(databaseRunId)) {
            throw new IllegalStateException(
                    "generated reference database must belong to the current run");
        }

        String username = require(environment, "POSTGRES_REFERENCE_USERNAME");
        String password = require(environment, "POSTGRES_REFERENCE_PASSWORD");
        String locations = require(environment, "POSTGRES_REFERENCE_LOCATIONS");
        if (!LOCATION.equals(locations)) {
            throw new IllegalStateException(
                    "POSTGRES_REFERENCE_LOCATIONS must select only migration_postgresql");
        }

        String baselineId = require(environment, "POSTGRES_REFERENCE_BASELINE_ID");
        if (!BASELINE_ID.matcher(baselineId).matches()) {
            throw new IllegalStateException("POSTGRES_REFERENCE_BASELINE_ID is invalid");
        }

        return new Settings(
                action,
                target,
                url,
                username,
                password,
                locations,
                runId,
                databaseName,
                baselineId);
    }

    static Flyway configure(Settings settings, BaselineManifest baseline) {
        requireAllowedTarget(settings.target());
        return Flyway.configure()
                .dataSource(settings.url(), settings.username(), settings.password())
                .locations(settings.locations())
                .target(settings.target())
                .baselineVersion(baseline.baselineVersion())
                .baselineDescription(baseline.baselineDescription())
                .baselineOnMigrate(false)
                .validateOnMigrate(true)
                .validateMigrationNaming(true)
                .outOfOrder(false)
                .cleanDisabled(true)
                .skipDefaultCallbacks(true)
                .failOnMissingLocations(true)
                .createSchemas(false)
                .connectRetries(0)
                .load();
    }

    static void assertMigrationResourcesApproved(List<String> resources) {
        if (resources.isEmpty()) {
            throw new IllegalStateException("PostgreSQL migration location is empty");
        }
        for (String resource : resources) {
            String fileName = resource.substring(resource.lastIndexOf('/') + 1);
            if (REPEATABLE_MIGRATION.matcher(fileName).matches()) {
                throw new IllegalStateException(
                        "REFERENCE_REPEATABLE_MIGRATION_UNAPPROVED: " + fileName);
            }
            if (SQL_CALLBACK.matcher(fileName).matches()) {
                throw new IllegalStateException("REFERENCE_CALLBACK_UNAPPROVED: " + fileName);
            }
        }
    }

    static void assertBaselineEligible(
            Settings settings,
            BaselineManifest manifest,
            DatabaseSnapshot snapshot) {
        if (!settings.databaseName().equals(snapshot.databaseName())) {
            throw new IllegalStateException("baseline database does not match the generated reference DB");
        }
        if (snapshot.schemaObjectCount() <= 0) {
            throw new IllegalStateException("baseline schema must be non-empty");
        }
        if (snapshot.nonEmptyTableCount() != 0) {
            throw new IllegalStateException("baseline schema must contain zero business rows");
        }
        if (snapshot.historyTableExists()) {
            throw new IllegalStateException("baseline schema must not already contain Flyway history");
        }
        requireReferenceTables(snapshot);
        assertPreV61Shape(snapshot.partyApplicationColumns());
        if (!manifest.schemaFingerprint().equals(snapshot.schemaFingerprint())) {
            throw new IllegalStateException("authoritative baseline schema fingerprint mismatch");
        }
    }

    static void assertPostMigration(
            String target,
            DatabaseSnapshot snapshot,
            List<String> appliedVersions) {
        requireAllowedTarget(target);
        if (!snapshot.historyTableExists()) {
            throw new IllegalStateException("Flyway history is missing after target migration");
        }
        requireReferenceTables(snapshot);
        if (appliedVersions.isEmpty()) {
            throw new IllegalStateException("Flyway applied history is empty");
        }

        MigrationVersion targetVersion = MigrationVersion.fromVersion(target);
        MigrationVersion latest = null;
        Set<String> unique = new HashSet<>();
        for (String version : appliedVersions) {
            if (!unique.add(version)) {
                throw new IllegalStateException("duplicate Flyway version in applied history: " + version);
            }
            MigrationVersion parsed = MigrationVersion.fromVersion(version);
            if (parsed.compareTo(targetVersion) > 0) {
                throw new IllegalStateException(
                        "applied migration exists above target " + target + ": " + version);
            }
            if (latest == null || parsed.compareTo(latest) > 0) {
                latest = parsed;
            }
        }
        if (latest == null || !targetVersion.equals(latest)) {
            throw new IllegalStateException(
                    "latest applied Flyway version must equal target " + target);
        }

        if ("60".equals(target)) {
            assertPreV61Shape(snapshot.partyApplicationColumns());
            return;
        }
        assertExactV61Shape(snapshot.partyApplicationColumns());
    }

    static MigrationSummary requireIdempotent(
            MigrationOutcome first,
            MigrationOutcome second,
            String target) {
        requireAllowedTarget(target);
        if (second.applied() != 0) {
            throw new IllegalStateException(
                    "second target-pinned migration execution must apply zero migrations");
        }
        String latest = second.latestVersion() != null
                ? second.latestVersion()
                : first.latestVersion();
        if (!target.equals(latest)) {
            throw new IllegalStateException("latest migration version must equal target " + target);
        }
        return new MigrationSummary(first.applied(), second.applied(), latest);
    }

    static String sha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return hex(digest.digest(content));
        } catch (NoSuchAlgorithmException exc) {
            throw new IllegalStateException("SHA-256 is unavailable", exc);
        }
    }

    public static void main(String[] args) {
        Settings settings = settings(System.getenv(), args);
        ClassLoader classLoader = PostgresFlywayReferenceCli.class.getClassLoader();
        assertMigrationResourcesApproved(listMigrationResources(classLoader));
        BaselineBundle bundle = BaselineBundle.load(classLoader, settings.baselineId());
        Flyway flyway = configure(settings, bundle.manifest());

        switch (settings.action()) {
            case BASELINE -> runBaseline(settings, bundle.manifest(), flyway);
            case MIGRATE -> runMigrate(settings, bundle.manifest(), flyway);
            case INFO -> runInfo(settings, flyway);
            case VALIDATE -> runValidate(settings, flyway);
        }
    }

    private static void runBaseline(
            Settings settings,
            BaselineManifest manifest,
            Flyway flyway) {
        DatabaseSnapshot before = inspect(settings);
        assertBaselineEligible(settings, manifest, before);
        BaselineResult result = flyway.baseline();
        if (!result.successfullyBaselined || !manifest.baselineVersion().equals(result.baselineVersion)) {
            throw new IllegalStateException("explicit Flyway baseline did not produce the approved row");
        }
        assertApprovedBaselineHistory(flyway.info(), manifest);
        printPass(settings, 0, manifest.baselineVersion());
    }

    private static void runMigrate(
            Settings settings,
            BaselineManifest manifest,
            Flyway flyway) {
        assertApprovedBaselineHistory(flyway.info(), manifest);
        MigrateResult first = flyway.migrate();
        MigrateResult second = flyway.migrate();
        MigrationSummary summary = requireIdempotent(
                migrationOutcome(first),
                migrationOutcome(second),
                settings.target());

        MigrationInfoService info = flyway.info();
        assertNoRepeatables(info.all());
        List<String> appliedVersions = appliedVersions(info);
        DatabaseSnapshot after = inspect(settings);
        assertPostMigration(settings.target(), after, appliedVersions);
        printPass(settings, summary.firstRunApplied(), summary.latestVersion());
    }

    private static void runInfo(Settings settings, Flyway flyway) {
        MigrationInfoService info = flyway.info();
        assertNoRepeatables(info.all());
        assertHistoryAtOrBelowTarget(info, settings.target());
        MigrationInfo current = info.current();
        printPass(settings, 0, current == null || current.getVersion() == null
                ? "none"
                : current.getVersion().getVersion());
    }

    private static void runValidate(Settings settings, Flyway flyway) {
        MigrationInfoService info = flyway.info();
        assertNoRepeatables(info.all());
        assertHistoryAtOrBelowTarget(info, settings.target());
        ValidateResult result = flyway.validateWithResult();
        if (!result.validationSuccessful) {
            throw new IllegalStateException("Flyway validation failed: " + result.getAllErrorMessages());
        }
        MigrationInfo current = info.current();
        printPass(settings, 0, current == null || current.getVersion() == null
                ? "none"
                : current.getVersion().getVersion());
    }

    private static void assertApprovedBaselineHistory(
            MigrationInfoService info,
            BaselineManifest manifest) {
        List<MigrationInfo> applied = Arrays.stream(info.applied())
                .filter(item -> item.getVersion() != null)
                .sorted(Comparator.comparing(MigrationInfo::getVersion))
                .toList();
        if (applied.isEmpty()) {
            throw new IllegalStateException("approved Flyway baseline history is missing");
        }
        MigrationInfo baseline = applied.get(0);
        if (!manifest.baselineVersion().equals(baseline.getVersion().getVersion())
                || !manifest.baselineDescription().equals(baseline.getDescription())
                || !"BASELINE".equals(baseline.getState().name())) {
            throw new IllegalStateException("Flyway baseline history does not match approved provenance");
        }
    }

    private static void assertNoRepeatables(MigrationInfo[] migrations) {
        for (MigrationInfo migration : migrations) {
            if (migration.isRepeatable()) {
                throw new IllegalStateException(
                        "REFERENCE_REPEATABLE_MIGRATION_UNAPPROVED: " + migration.getScript());
            }
        }
    }

    private static void assertHistoryAtOrBelowTarget(MigrationInfoService info, String target) {
        MigrationVersion targetVersion = MigrationVersion.fromVersion(target);
        for (MigrationInfo migration : info.applied()) {
            if (migration.getVersion() != null
                    && migration.getVersion().compareTo(targetVersion) > 0) {
                throw new IllegalStateException(
                        "applied migration exists above target " + target + ": "
                                + migration.getVersion().getVersion());
            }
        }
    }

    private static List<String> appliedVersions(MigrationInfoService info) {
        return Arrays.stream(info.applied())
                .filter(item -> item.getVersion() != null)
                .map(item -> item.getVersion().getVersion())
                .toList();
    }

    private static MigrationOutcome migrationOutcome(MigrateResult result) {
        return new MigrationOutcome(result.migrationsExecuted, result.targetSchemaVersion);
    }

    private static void printPass(
            Settings settings,
            int applied,
            String latestVersion) {
        if (latestVersion != null
                && !"none".equals(latestVersion)
                && !latestVersion.matches("[0-9]+(?:[._][0-9]+)*")) {
            throw new IllegalStateException("Flyway version has an unexpected format");
        }
        System.out.println(
                "{\"status\":\"PASS\",\"action\":\""
                        + settings.action().command()
                        + "\",\"target\":\""
                        + settings.target()
                        + "\",\"applied\":"
                        + applied
                        + ",\"latest_version\":\""
                        + (latestVersion == null ? "none" : latestVersion)
                        + "\"}");
    }

    static List<String> listMigrationResources(ClassLoader classLoader) {
        String resourcePath = LOCATION.substring("classpath:".length());
        TreeSet<String> resources = new TreeSet<>();
        try {
            Enumeration<URL> urls = classLoader.getResources(resourcePath);
            while (urls.hasMoreElements()) {
                URL url = urls.nextElement();
                if ("file".equals(url.getProtocol())) {
                    collectFileResources(Path.of(url.toURI()), resources);
                } else if ("jar".equals(url.getProtocol())) {
                    collectJarResources(url, resourcePath, resources);
                } else {
                    throw new IllegalStateException(
                            "unsupported migration resource protocol: " + url.getProtocol());
                }
            }
        } catch (Exception exc) {
            if (exc instanceof IllegalStateException stateException) {
                throw stateException;
            }
            throw new IllegalStateException("cannot enumerate PostgreSQL migration resources", exc);
        }
        return List.copyOf(resources);
    }

    private static void collectFileResources(Path directory, Set<String> resources) throws IOException {
        try (var paths = Files.walk(directory)) {
            paths.filter(Files::isRegularFile)
                    .map(path -> directory.relativize(path).toString().replace('\\', '/'))
                    .forEach(resources::add);
        }
    }

    private static void collectJarResources(
            URL url,
            String resourcePath,
            Set<String> resources) throws IOException {
        JarURLConnection connection = (JarURLConnection) url.openConnection();
        connection.setUseCaches(false);
        try (JarFile jar = connection.getJarFile()) {
            Enumeration<JarEntry> entries = jar.entries();
            String prefix = resourcePath + "/";
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!entry.isDirectory() && entry.getName().startsWith(prefix)) {
                    resources.add(entry.getName().substring(prefix.length()));
                }
            }
        }
    }

    private static DatabaseSnapshot inspect(Settings settings) {
        try (Connection connection = DriverManager.getConnection(
                settings.url(), settings.username(), settings.password())) {
            connection.setReadOnly(true);
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            connection.setAutoCommit(false);
            try {
                DatabaseSnapshot snapshot = inspect(connection);
                connection.rollback();
                return snapshot;
            } catch (RuntimeException | SQLException exc) {
                connection.rollback();
                throw exc;
            }
        } catch (SQLException exc) {
            throw new IllegalStateException("reference database inspection failed", exc);
        }
    }

    private static DatabaseSnapshot inspect(Connection connection) throws SQLException {
        String databaseName = scalarString(connection, "SELECT current_database()");
        int objectCount = scalarInt(connection, """
                SELECT count(*)
                FROM pg_class c
                JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname NOT IN ('pg_catalog', 'information_schema')
                  AND n.nspname !~ '^pg_toast'
                  AND c.relkind IN ('r', 'p', 'v', 'm', 'S')
                  AND c.relname <> 'flyway_schema_history'
                """);
        boolean historyExists = scalarBoolean(
                connection,
                "SELECT to_regclass('public.flyway_schema_history') IS NOT NULL");

        Set<String> tables = new TreeSet<>();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("""
                     SELECT tablename
                     FROM pg_tables
                     WHERE schemaname = 'public'
                       AND tablename <> 'flyway_schema_history'
                     ORDER BY tablename
                     """)) {
            while (result.next()) {
                tables.add(result.getString(1));
            }
        }

        Map<String, String> columns = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("""
                     SELECT a.attname, pg_catalog.format_type(a.atttypid, a.atttypmod)
                     FROM pg_attribute a
                     JOIN pg_class c ON c.oid = a.attrelid
                     JOIN pg_namespace n ON n.oid = c.relnamespace
                     WHERE n.nspname = 'public'
                       AND c.relname = 'party_applications'
                       AND a.attnum > 0
                       AND NOT a.attisdropped
                     ORDER BY a.attnum
                     """)) {
            while (result.next()) {
                columns.put(result.getString(1).toLowerCase(Locale.ROOT), result.getString(2));
            }
        }

        int nonEmptyTableCount = countNonEmptyTables(connection);
        String fingerprint = catalogFingerprint(connection);
        return new DatabaseSnapshot(
                databaseName,
                objectCount,
                nonEmptyTableCount,
                historyExists,
                Set.copyOf(tables),
                Map.copyOf(columns),
                fingerprint);
    }

    private static int countNonEmptyTables(Connection connection) throws SQLException {
        List<String[]> tableNames = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("""
                     SELECT schemaname, tablename
                     FROM pg_tables
                     WHERE schemaname NOT IN ('pg_catalog', 'information_schema')
                       AND schemaname !~ '^pg_toast'
                       AND tablename <> 'flyway_schema_history'
                     ORDER BY schemaname, tablename
                     """)) {
            while (result.next()) {
                tableNames.add(new String[] {result.getString(1), result.getString(2)});
            }
        }

        int nonEmpty = 0;
        for (String[] table : tableNames) {
            String query = "SELECT EXISTS (SELECT 1 FROM "
                    + quoteIdentifier(table[0])
                    + "."
                    + quoteIdentifier(table[1])
                    + " LIMIT 1)";
            if (scalarBoolean(connection, query)) {
                nonEmpty++;
            }
        }
        return nonEmpty;
    }

    private static String catalogFingerprint(Connection connection) throws SQLException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (int queryIndex = 0; queryIndex < FINGERPRINT_QUERIES.size(); queryIndex++) {
                updateDigest(digest, "query:" + queryIndex);
                try (Statement statement = connection.createStatement();
                     ResultSet result = statement.executeQuery(FINGERPRINT_QUERIES.get(queryIndex))) {
                    ResultSetMetaData metadata = result.getMetaData();
                    while (result.next()) {
                        updateDigest(digest, "row");
                        for (int column = 1; column <= metadata.getColumnCount(); column++) {
                            String value = result.getString(column);
                            updateDigest(digest, value == null ? "<NULL>" : value);
                        }
                    }
                }
            }
            return hex(digest.digest());
        } catch (NoSuchAlgorithmException exc) {
            throw new IllegalStateException("SHA-256 is unavailable", exc);
        }
    }

    private static void updateDigest(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(Integer.toString(bytes.length).getBytes(StandardCharsets.US_ASCII));
        digest.update((byte) ':');
        digest.update(bytes);
        digest.update((byte) '\n');
    }

    private static String scalarString(Connection connection, String query) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(query)) {
            if (!result.next()) {
                throw new IllegalStateException("scalar query returned no row");
            }
            return result.getString(1);
        }
    }

    private static int scalarInt(Connection connection, String query) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(query)) {
            if (!result.next()) {
                throw new IllegalStateException("scalar query returned no row");
            }
            return result.getInt(1);
        }
    }

    private static boolean scalarBoolean(Connection connection, String query) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(query)) {
            if (!result.next()) {
                throw new IllegalStateException("scalar query returned no row");
            }
            return result.getBoolean(1);
        }
    }

    private static String quoteIdentifier(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private static void requireReferenceTables(DatabaseSnapshot snapshot) {
        if (!snapshot.tables().contains("parties")
                || !snapshot.tables().contains("party_applications")) {
            throw new IllegalStateException(
                    "reference schema must contain parties and party_applications");
        }
    }

    private static void assertPreV61Shape(Map<String, String> columns) {
        if (columns.containsKey("payment_key") || columns.containsKey("order_id")) {
            throw new IllegalStateException(
                    "party_applications must have the pre-V61 payment column shape");
        }
    }

    private static void assertExactV61Shape(Map<String, String> columns) {
        String paymentKey = columns.get("payment_key");
        String orderId = columns.get("order_id");
        if (!"character varying(200)".equals(paymentKey)) {
            throw new IllegalStateException(
                    "V61 payment_key must be exactly character varying(200)");
        }
        if (!"character varying(100)".equals(orderId)) {
            throw new IllegalStateException(
                    "V61 order_id must be exactly character varying(100)");
        }
    }

    private static void requireAllowedTarget(String target) {
        if (!"60".equals(target) && !"61".equals(target)) {
            throw new IllegalStateException("reference target must be exactly 60 or 61");
        }
    }

    private static void assertLoopback(String host) {
        String normalized = host.startsWith("[") && host.endsWith("]")
                ? host.substring(1, host.length() - 1)
                : host;
        try {
            for (InetAddress address : InetAddress.getAllByName(normalized)) {
                if (!address.isLoopbackAddress()) {
                    throw new IllegalStateException(
                            "POSTGRES_REFERENCE_URL must resolve only to loopback addresses");
                }
            }
        } catch (UnknownHostException exc) {
            throw new IllegalStateException("loopback host cannot be resolved", exc);
        }
    }

    private static String require(Map<String, String> environment, String key) {
        String value = environment.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(key + " is required");
        }
        return value.trim();
    }

    private static void requireExact(
            Map<String, String> environment,
            String key,
            String expected) {
        String actual = require(environment, key);
        if (!expected.equals(actual.toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException(key + " must be " + expected);
        }
    }

    private static String requiredProperty(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("baseline manifest property is required: " + key);
        }
        return value.trim();
    }

    private static boolean requiredBoolean(Properties properties, String key) {
        String value = requiredProperty(properties, key);
        if (!"true".equals(value) && !"false".equals(value)) {
            throw new IllegalStateException("baseline manifest boolean is invalid: " + key);
        }
        return Boolean.parseBoolean(value);
    }

    private static int requiredInt(Properties properties, String key) {
        try {
            return Integer.parseInt(requiredProperty(properties, key));
        } catch (NumberFormatException exc) {
            throw new IllegalStateException("baseline manifest integer is invalid: " + key, exc);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        }
        return result.toString();
    }

    enum Action {
        BASELINE("baseline"),
        MIGRATE("migrate"),
        INFO("info"),
        VALIDATE("validate");

        private final String command;

        Action(String command) {
            this.command = command;
        }

        String command() {
            return command;
        }

        static Action parse(String value) {
            for (Action action : values()) {
                if (action.command.equals(value)) {
                    return action;
                }
            }
            throw new IllegalStateException(
                    "reference action must be baseline, migrate, info, or validate");
        }
    }

    record Settings(
            Action action,
            String target,
            String url,
            String username,
            String password,
            String locations,
            String runId,
            String databaseName,
            String baselineId) {
    }

    record BaselineManifest(
            String baselineVersion,
            String baselineDescription,
            String artifactSha256,
            String schemaFingerprint,
            String sourceType,
            String sourceOwner,
            String sourceTimestamp,
            String sourceCommit,
            String extractionMethod,
            String reviewerOperatorApproval) {

        static BaselineManifest parse(
                Properties properties,
                byte[] sqlArtifact,
                byte[] ownershipMatrix) {
            if (!"AUTHORITATIVE_BASELINE_ACCEPTED".equals(
                    requiredProperty(properties, "status"))) {
                throw new IllegalStateException("authoritative baseline status is not accepted");
            }

            String baselineVersion = requiredProperty(properties, "baseline.version");
            String baselineDescription = requiredProperty(properties, "baseline.description");
            if (!BASELINE_VERSION.equals(baselineVersion)
                    || !BASELINE_DESCRIPTION.equals(baselineDescription)) {
                throw new IllegalStateException("baseline version or description is not approved");
            }

            String declaredArtifactSha = requiredProperty(properties, "artifact.sha256");
            if (!SHA_256.matcher(declaredArtifactSha).matches()
                    || !declaredArtifactSha.equals(sha256(sqlArtifact))) {
                throw new IllegalStateException("baseline artifact SHA-256 mismatch");
            }
            String declaredOwnershipSha = requiredProperty(
                    properties, "ownership.matrix.sha256");
            if (!SHA_256.matcher(declaredOwnershipSha).matches()
                    || !declaredOwnershipSha.equals(sha256(ownershipMatrix))) {
                throw new IllegalStateException("baseline ownership matrix SHA-256 mismatch");
            }

            if (!FINGERPRINT_VERSION.equals(
                    requiredProperty(properties, "schema.fingerprint.version"))) {
                throw new IllegalStateException("baseline schema fingerprint version is unsupported");
            }
            String schemaFingerprint = requiredProperty(
                    properties, "schema.fingerprint.sha256");
            if (!SHA_256.matcher(schemaFingerprint).matches()) {
                throw new IllegalStateException("baseline schema fingerprint is invalid");
            }

            String sourceType = requiredProperty(properties, "source.type");
            String normalizedSourceType = sourceType.toLowerCase(Locale.ROOT);
            if (Set.of("current_source_clone", "target_b", "current_target_clone")
                    .contains(normalizedSourceType)) {
                throw new IllegalStateException(
                        "baseline must not be derived only from the current source or target B");
            }

            String sourceOwner = requiredProperty(properties, "source.owner");
            String sourceTimestamp = properties.getProperty("source.timestamp", "").trim();
            String sourceCommit = properties.getProperty("source.commit", "").trim();
            if (sourceTimestamp.isBlank() && sourceCommit.isBlank()) {
                throw new IllegalStateException(
                        "baseline provenance requires a source timestamp or commit");
            }
            String extractionMethod = requiredProperty(properties, "extraction.method");
            String approval = requiredProperty(properties, "reviewer.operator.approval");

            if (!requiredBoolean(properties, "schema.only")) {
                throw new IllegalStateException("baseline artifact must be schema-only");
            }
            if (requiredBoolean(properties, "contains.business.data")) {
                throw new IllegalStateException("baseline artifact must contain no business data");
            }
            if (requiredBoolean(properties, "contains.credentials")) {
                throw new IllegalStateException("baseline artifact must contain no credentials");
            }
            if (requiredBoolean(properties, "contains.acl")) {
                throw new IllegalStateException("baseline artifact must contain no ACL statements");
            }
            if (!requiredBoolean(properties, "required.object.closure.complete")) {
                throw new IllegalStateException("baseline required-object closure is incomplete");
            }
            if (requiredInt(properties, "unknown.owner.count") != 0) {
                throw new IllegalStateException("baseline contains an unknown owner object");
            }
            if (requiredInt(properties, "ownership.overlap.count") != 0) {
                throw new IllegalStateException("baseline contains overlapping object ownership");
            }
            if (requiredBoolean(properties, "derived.only.from.current.source")) {
                throw new IllegalStateException(
                        "baseline must not be derived only from the current source");
            }
            if (requiredBoolean(properties, "party_applications.payment_key")
                    || requiredBoolean(properties, "party_applications.order_id")) {
                throw new IllegalStateException("baseline party_applications is not pre-V61");
            }

            assertSchemaOnlySql(sqlArtifact);
            assertOwnershipMatrix(ownershipMatrix);
            return new BaselineManifest(
                    baselineVersion,
                    baselineDescription,
                    declaredArtifactSha,
                    schemaFingerprint,
                    sourceType,
                    sourceOwner,
                    sourceTimestamp,
                    sourceCommit,
                    extractionMethod,
                    approval);
        }

        private static void assertSchemaOnlySql(byte[] sqlArtifact) {
            String sql = new String(sqlArtifact, StandardCharsets.UTF_8);
            if (DATA_STATEMENT.matcher(sql).find()) {
                throw new IllegalStateException("baseline artifact is not schema-only");
            }
            if (ACL_STATEMENT.matcher(sql).find()) {
                throw new IllegalStateException("baseline artifact contains an ACL or owner statement");
            }
            if (CREDENTIAL_STATEMENT.matcher(sql).find()) {
                throw new IllegalStateException("baseline artifact contains credentials");
            }
            if (!PARTIES_DDL.matcher(sql).find()
                    || !PARTY_APPLICATIONS_DDL.matcher(sql).find()) {
                throw new IllegalStateException(
                        "baseline artifact is missing required parties tables");
            }
        }

        private static void assertOwnershipMatrix(byte[] ownershipMatrix) {
            String content = new String(ownershipMatrix, StandardCharsets.UTF_8);
            String[] lines = content.split("\\R");
            if (lines.length < 3
                    || !"object_type\tobject_name\towner_category".equals(lines[0])) {
                throw new IllegalStateException("baseline ownership matrix header is invalid");
            }
            Set<String> objects = new HashSet<>();
            Set<String> required = new HashSet<>(Set.of(
                    "relation\tpublic.parties",
                    "relation\tpublic.party_applications"));
            for (int index = 1; index < lines.length; index++) {
                if (lines[index].isBlank()) {
                    continue;
                }
                String[] fields = lines[index].split("\\t", -1);
                if (fields.length != 3
                        || fields[0].isBlank()
                        || fields[1].isBlank()
                        || !OWNER_CATEGORIES.contains(fields[2])) {
                    throw new IllegalStateException(
                            "baseline ownership matrix has an invalid owner category");
                }
                String objectKey = fields[0] + "\t" + fields[1];
                if (!objects.add(objectKey)) {
                    throw new IllegalStateException(
                            "baseline ownership matrix contains overlapping object ownership");
                }
                if (required.contains(objectKey)
                        && !"backend_postgresql_baseline".equals(fields[2])) {
                    throw new IllegalStateException(
                            "required parties objects must be owned by the PostgreSQL baseline");
                }
                required.remove(objectKey);
            }
            if (!required.isEmpty()) {
                throw new IllegalStateException(
                        "baseline ownership matrix is missing required parties objects");
            }
        }
    }

    record BaselineBundle(
            BaselineManifest manifest,
            byte[] sqlArtifact,
            byte[] ownershipMatrix) {

        static BaselineBundle load(ClassLoader classLoader, String baselineId) {
            String root = REFERENCE_RESOURCE_ROOT + "/" + baselineId;
            Properties properties = new Properties();
            byte[] sqlArtifact;
            byte[] ownershipMatrix;
            try (InputStream manifestStream = classLoader.getResourceAsStream(
                    root + "/baseline.properties");
                 InputStream sqlStream = classLoader.getResourceAsStream(root + "/baseline.sql");
                 InputStream ownershipStream = classLoader.getResourceAsStream(
                         root + "/ownership.tsv")) {
                if (manifestStream == null || sqlStream == null || ownershipStream == null) {
                    throw new IllegalStateException(
                            "AUTHORITATIVE_BASELINE_ARTIFACT_MISSING: " + baselineId);
                }
                properties.load(manifestStream);
                sqlArtifact = sqlStream.readAllBytes();
                ownershipMatrix = ownershipStream.readAllBytes();
            } catch (IOException exc) {
                throw new IllegalStateException("cannot load authoritative baseline bundle", exc);
            }
            return new BaselineBundle(
                    BaselineManifest.parse(properties, sqlArtifact, ownershipMatrix),
                    sqlArtifact,
                    ownershipMatrix);
        }
    }

    record DatabaseSnapshot(
            String databaseName,
            int schemaObjectCount,
            int nonEmptyTableCount,
            boolean historyTableExists,
            Set<String> tables,
            Map<String, String> partyApplicationColumns,
            String schemaFingerprint) {

        DatabaseSnapshot withSchemaObjectCount(int value) {
            return new DatabaseSnapshot(
                    databaseName,
                    value,
                    nonEmptyTableCount,
                    historyTableExists,
                    tables,
                    partyApplicationColumns,
                    schemaFingerprint);
        }

        DatabaseSnapshot withNonEmptyTableCount(int value) {
            return new DatabaseSnapshot(
                    databaseName,
                    schemaObjectCount,
                    value,
                    historyTableExists,
                    tables,
                    partyApplicationColumns,
                    schemaFingerprint);
        }

        DatabaseSnapshot withHistoryTableExists(boolean value) {
            return new DatabaseSnapshot(
                    databaseName,
                    schemaObjectCount,
                    nonEmptyTableCount,
                    value,
                    tables,
                    partyApplicationColumns,
                    schemaFingerprint);
        }

        DatabaseSnapshot withPartyApplicationColumns(Map<String, String> value) {
            return new DatabaseSnapshot(
                    databaseName,
                    schemaObjectCount,
                    nonEmptyTableCount,
                    historyTableExists,
                    tables,
                    Map.copyOf(value),
                    schemaFingerprint);
        }

        DatabaseSnapshot withSchemaFingerprint(String value) {
            return new DatabaseSnapshot(
                    databaseName,
                    schemaObjectCount,
                    nonEmptyTableCount,
                    historyTableExists,
                    tables,
                    partyApplicationColumns,
                    value);
        }
    }

    record MigrationOutcome(int applied, String latestVersion) {
        MigrationOutcome {
            if (applied < 0) {
                throw new IllegalArgumentException("applied migration count cannot be negative");
            }
        }
    }

    record MigrationSummary(int firstRunApplied, int secondRunApplied, String latestVersion) {
    }
}
