package com.example.common.config;

import java.util.ArrayList;
import java.util.List;

import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * prod 환경에서 primary/baseball datasource 토폴로지를 fail-fast로 검증한다.
 * - primary: Oracle
 * - baseball: PostgreSQL
 */
@Component
@Profile("prod")
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

        String primaryDriver = normalize(resolveDriver(primaryDataSourceProperties));
        String baseballDriver = normalize(resolveDriver(baseballDataSourceProperties));
        String baseballUrl = normalize(baseballDataSourceProperties.getUrl());
        String baseballUsername = normalize(baseballDataSourceProperties.getUsername());
        String baseballPassword = normalize(baseballDataSourceProperties.getPassword());

        if (!primaryDriver.contains("oracle")) {
            failures.add("primary datasource driver must be Oracle, but was: " + safe(primaryDriver));
        }

        if (!baseballDriver.contains("postgresql")) {
            failures.add("baseball datasource driver must be PostgreSQL, but was: " + safe(baseballDriver));
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

        if (!failures.isEmpty()) {
            String message = String.join(" | ", failures);
            log.error("db.topology.validation.fail {}", message);
            throw new IllegalStateException("DB topology validation failed: " + message);
        }

        log.info(
                "db.topology.validation.ok primaryDriver={} baseballDriver={} baseballUrlConfigured={}",
                primaryDriver,
                baseballDriver,
                !isBlank(baseballUrl)
        );
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

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String safe(String value) {
        return isBlank(value) ? "<empty>" : value;
    }
}
