package com.example.common.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * prod/dev-adb 환경에서 primary/baseball datasource 토폴로지를 fail-fast로 검증한다.
 * - primary: Oracle 또는 PostgreSQL (Oracle 탈출 이행기 동안 둘 다 허용한다)
 * - baseball: PostgreSQL
 *
 * primary 는 두 엔진을 모두 허용하되, driver 와 url 이 <em>같은</em> 엔진을 가리키는지는
 * 계속 강제한다. 엔진이 엇갈린 설정(예: Oracle driver + PostgreSQL url)은 기동 시점이 아니라
 * 첫 쿼리에서 터지므로, fail-fast 로 잡는 값어치가 가장 큰 대상이다.
 */
@Component
@Profile("prod | dev-adb")
@Slf4j
public class DbTopologyStartupValidator {

    private final Environment environment;
    private final DataSourceProperties primaryDataSourceProperties;
    private final DataSourceProperties baseballDataSourceProperties;

    public DbTopologyStartupValidator(
            Environment environment,
            @Qualifier("primaryDataSourceProperties") DataSourceProperties primaryDataSourceProperties,
            @Qualifier("stadiumDataSourceProperties") DataSourceProperties baseballDataSourceProperties
    ) {
        this.environment = environment;
        this.primaryDataSourceProperties = primaryDataSourceProperties;
        this.baseballDataSourceProperties = baseballDataSourceProperties;
    }

    @PostConstruct
    public void validate() {
        List<String> failures = new ArrayList<>();
        String primaryUrlEnvKey = isDevAdbProfile() ? "DEV_ADB_URL" : "SPRING_DATASOURCE_URL";
        String primaryUsernameEnvKey = isDevAdbProfile() ? "DEV_ADB_USERNAME" : "SPRING_DATASOURCE_USERNAME";
        String primaryPasswordEnvKey = isDevAdbProfile() ? "DEV_ADB_PASSWORD" : "SPRING_DATASOURCE_PASSWORD";

        String primaryDriver = normalize(resolveDriver(primaryDataSourceProperties));
        String primaryUrl = normalize(primaryDataSourceProperties.getUrl());
        String baseballDriver = normalize(resolveDriver(baseballDataSourceProperties));
        String baseballUrl = normalize(baseballDataSourceProperties.getUrl());
        String baseballUsername = normalize(baseballDataSourceProperties.getUsername());
        String baseballPassword = normalize(baseballDataSourceProperties.getPassword());

        if (isBlank(primaryUrl)) {
            failures.add(primaryUrlEnvKey + " is required in " + activeProfileLabel());
        }

        boolean oracleUrl = primaryUrl.startsWith("jdbc:oracle:");
        boolean postgresUrl = primaryUrl.startsWith("jdbc:postgresql:");

        if (!oracleUrl && !postgresUrl) {
            failures.add("primary datasource url must be Oracle or PostgreSQL JDBC, but was: " + safe(primaryUrl));
        }

        if (oracleUrl && !primaryDriver.contains("oracle")) {
            failures.add("primary datasource url is Oracle, but driver was: " + safe(primaryDriver));
        }

        if (postgresUrl && !primaryDriver.contains("postgresql")) {
            failures.add("primary datasource url is PostgreSQL, but driver was: " + safe(primaryDriver));
        }

        if (isDevAdbProfile()) {
            requireEnvironmentValue(failures, primaryUrlEnvKey);
            requireEnvironmentValue(failures, primaryUsernameEnvKey);
            requireEnvironmentValue(failures, primaryPasswordEnvKey);
        }

        // 야구 데이터는 PostgreSQL 또는 Oracle(ADB) 중 하나에 있을 수 있다.
        // primary 와 마찬가지로 엔진 자체는 열어두되, driver 와 url 이 <em>같은</em>
        // 엔진을 가리키는지는 계속 강제한다 — 엇갈린 설정은 기동이 아니라 첫 쿼리에서
        // 터지므로 fail-fast 로 잡는 값어치가 크다.
        boolean baseballOracleUrl = baseballUrl.startsWith("jdbc:oracle:");
        boolean baseballPostgresUrl = baseballUrl.startsWith("jdbc:postgresql:");

        if (!isBlank(baseballUrl) && !baseballOracleUrl && !baseballPostgresUrl) {
            failures.add("baseball datasource url must be Oracle or PostgreSQL JDBC, but was: " + safe(baseballUrl));
        }
        if (baseballOracleUrl && !baseballDriver.contains("oracle")) {
            failures.add("baseball datasource url is Oracle, but driver was: " + safe(baseballDriver));
        }
        if (baseballPostgresUrl && !baseballDriver.contains("postgresql")) {
            failures.add("baseball datasource url is PostgreSQL, but driver was: " + safe(baseballDriver));
        }

        if (isBlank(baseballUrl)) {
            failures.add("BASEBALL_DB_URL is required in prod");
        }
        if (isBlank(baseballUsername)) {
            failures.add("BASEBALL_DB_USERNAME is required in prod");
        }
        if (isBlank(baseballPassword)) {
            failures.add("BASEBALL_DB_PASSWORD is required in prod");
        }

        if (isBlank(environment.getProperty("BASEBALL_DB_URL"))) {
            failures.add("BASEBALL_DB_URL env var is missing");
        }
        if (isBlank(environment.getProperty("BASEBALL_DB_USERNAME"))) {
            failures.add("BASEBALL_DB_USERNAME env var is missing");
        }
        if (isBlank(environment.getProperty("BASEBALL_DB_PASSWORD"))) {
            failures.add("BASEBALL_DB_PASSWORD env var is missing");
        }
        // 월렛 검사는 url 이 jdbc:oracle:thin: 일 때만 동작하고 아니면 즉시 반환한다.
        // 야구 DB 가 ADB 로 옮겨가면 월렛이 필요한 쪽은 primary 가 아니라 이쪽이다.
        validateOracleWalletIfNeeded(failures, primaryDataSourceProperties.getUrl());
        validateOracleWalletIfNeeded(failures, baseballDataSourceProperties.getUrl());

        if (!failures.isEmpty()) {
            String message = String.join(" | ", failures);
            log.error("db.topology.validation.fail {}", message);
            throw new IllegalStateException("DB topology validation failed: " + message);
        }

        log.info(
                "db.topology.validation.ok profile={} primaryDriver={} baseballDriver={} baseballUrlConfigured={}",
                activeProfileLabel(),
                primaryDriver,
                baseballDriver,
                !isBlank(baseballUrl)
        );
    }

    private boolean isDevAdbProfile() {
        return environment.acceptsProfiles(Profiles.of("dev-adb"));
    }

    private String activeProfileLabel() {
        return isDevAdbProfile() ? "dev-adb" : "prod";
    }

    private void requireEnvironmentValue(List<String> failures, String key) {
        if (isBlank(environment.getProperty(key))) {
            failures.add(key + " env var is missing for " + activeProfileLabel());
        }
    }

    private String resolveDriver(DataSourceProperties properties) {
        try {
            String explicit = properties.getDriverClassName();
            if (!isBlank(explicit)) {
                return explicit;
            }
            return properties.determineDriverClassName();
        } catch (Exception ex) {
            return "";
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private void validateOracleWalletIfNeeded(List<String> failures, String datasourceUrl) {
        String url = datasourceUrl == null ? "" : datasourceUrl.trim();
        String resolvedUrl = url.toLowerCase();
        if (!resolvedUrl.startsWith("jdbc:oracle:thin:")) {
            return;
        }

        if (!usesTnsAlias(url)) {
            return;
        }

        String tnsAdmin = extractTnsAdmin(url);
        if (isBlank(tnsAdmin)) {
            tnsAdmin = environment.getProperty("TNS_ADMIN");
        }
        if (isBlank(tnsAdmin)) {
            tnsAdmin = environment.getProperty("ORACLE_TNS_ADMIN");
        }

        if (isBlank(tnsAdmin)) {
            failures.add("ORACLE_TNS_ADMIN is required for Oracle TNS alias datasource ("
                    + (isDevAdbProfile() ? "DEV_ADB_URL" : "SPRING_DATASOURCE_URL") + ")");
            return;
        }

        Path walletDir = Paths.get(tnsAdmin);
        if (!Files.exists(walletDir) || !Files.isDirectory(walletDir)) {
            failures.add("Oracle TNS_ADMIN path does not exist or is not a directory: " + tnsAdmin);
            return;
        }

        if (!Files.isReadable(walletDir)) {
            failures.add("Oracle wallet path is not readable: " + tnsAdmin);
            return;
        }

        if (!Files.exists(walletDir.resolve("tnsnames.ora"))) {
            failures.add("Oracle wallet path is missing tnsnames.ora: " + tnsAdmin);
            return;
        }

        boolean walletFileExists = Files.exists(walletDir.resolve("cwallet.sso"))
                || Files.exists(walletDir.resolve("ewallet.p12"));
        if (!walletFileExists) {
            failures.add("Oracle wallet path is missing cwallet.sso or ewallet.p12: " + tnsAdmin);
        }
    }

    private String extractTnsAdmin(String datasourceUrl) {
        int questionIndex = datasourceUrl.indexOf('?');
        if (questionIndex < 0 || questionIndex + 1 >= datasourceUrl.length()) {
            return "";
        }

        String query = datasourceUrl.substring(questionIndex + 1);
        for (String entry : query.split("&")) {
            int eq = entry.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = entry.substring(0, eq).trim();
            if ("tns_admin".equalsIgnoreCase(key)) {
                return entry.substring(eq + 1).trim();
            }
        }
        return "";
    }

    private boolean usesTnsAlias(String datasourceUrl) {
        int atIndex = datasourceUrl.indexOf('@');
        if (atIndex < 0 || atIndex + 1 >= datasourceUrl.length()) {
            return false;
        }

        String target = datasourceUrl.substring(atIndex + 1);
        int queryIndex = target.indexOf('?');
        if (queryIndex >= 0) {
            target = target.substring(0, queryIndex);
        }

        if (target.startsWith("//") || target.contains(":") || target.contains("/")) {
            return false;
        }
        return !isBlank(target);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String safe(String value) {
        return isBlank(value) ? "<empty>" : value;
    }
}
