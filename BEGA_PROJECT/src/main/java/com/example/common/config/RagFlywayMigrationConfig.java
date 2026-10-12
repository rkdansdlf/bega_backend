package com.example.common.config;

import java.util.Locale;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import lombok.extern.slf4j.Slf4j;

/**
 * Flyway executor for the dedicated Oracle RAG schema.
 *
 * <p>The AI runtime receives an {@code oracle+oracledb://} URL and deliberately
 * performs no DDL. This executor instead requires a separate JDBC URL and the
 * RAG schema-owner credential. It is absent unless explicitly enabled, so a normal
 * backend deployment cannot connect to or mutate the RAG database.
 */
@Slf4j
@Configuration
public class RagFlywayMigrationConfig {

    static final String HISTORY_TABLE = "flyway_rag_schema_history";
    static final String DEFAULT_LOCATION = "classpath:db/migration_rag_oracle/";

    @Bean(name = "ragFlyway")
    @ConditionalOnProperty(prefix = "rag.flyway", name = "enabled", havingValue = "true")
    @SuppressWarnings("unused")
    public Object ragFlywayBean(
            @Value("${rag.flyway.url:}") String url,
            @Value("${rag.flyway.username:}") String username,
            @Value("${rag.flyway.password:}") String password,
            @Value("${rag.flyway.locations:" + DEFAULT_LOCATION + "}") String locations,
            @Value("${rag.flyway.history-table:" + HISTORY_TABLE + "}") String historyTable) {
        if (url == null || !url.toLowerCase(Locale.ROOT).startsWith("jdbc:oracle:")) {
            throw new IllegalStateException(
                    "rag.flyway.enabled=true requires a dedicated jdbc:oracle: rag.flyway.url");
        }
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "rag.flyway.enabled=true requires rag.flyway.username and rag.flyway.password");
        }

        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("oracle.jdbc.OracleDriver");
        dataSource.setUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        return ragFlyway(dataSource, true, locations, historyTable);
    }

    public Flyway ragFlyway(DataSource dataSource, boolean enabled, String locations) {
        return ragFlyway(dataSource, enabled, locations, HISTORY_TABLE);
    }

    public Flyway ragFlyway(
            DataSource dataSource,
            boolean enabled,
            String locations,
            String historyTable) {
        if (!HISTORY_TABLE.equals(historyTable)) {
            throw new IllegalStateException(
                    "rag.flyway.history-table must use the dedicated RAG Flyway history table "
                            + HISTORY_TABLE);
        }
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations(locations.split(","))
                .table(historyTable)
                .baselineOnMigrate(false)
                .validateOnMigrate(true)
                .outOfOrder(false)
                .cleanDisabled(true)
                .load();

        if (!enabled) {
            log.info("rag.flyway.skipped enabled=false locations={} table={}", locations, historyTable);
            return flyway;
        }

        MigrateResult result = flyway.migrate();
        log.info(
                "rag.flyway.migrated locations={} table={} initialVersion={} targetVersion={} applied={}",
                locations,
                historyTable,
                result.initialSchemaVersion,
                result.targetSchemaVersion,
                result.migrationsExecuted);
        return flyway;
    }
}
