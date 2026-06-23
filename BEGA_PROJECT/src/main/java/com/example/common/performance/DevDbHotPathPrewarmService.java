package com.example.common.performance;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DevDbHotPathPrewarmService {

    private static final int MAX_CONNECTIONS_PER_DATASOURCE = 4;
    private static final String USER_EMAIL_SQL = """
            SELECT u.id
            FROM public.users u
            WHERE u.email = ?
            LIMIT 1
            """;
    private static final String GAME_RANGE_SQL = """
            SELECT g.game_id
            FROM public.game g
            LEFT JOIN public.game_metadata gm ON gm.game_id = g.game_id
            WHERE g.game_date BETWEEN ? AND ?
              AND g.is_dummy IS NOT TRUE
              AND g.game_id NOT LIKE 'MOCK%'
            ORDER BY g.game_date ASC, g.game_id ASC
            LIMIT 20
            """;
    private static final String SEASON_SQL = """
            SELECT ks.season_id
            FROM public.kbo_seasons ks
            WHERE ks.season_id = ?
            LIMIT 1
            """;

    private final DataSource primaryDataSource;
    private final DataSource stadiumDataSource;
    private final boolean enabled;
    private final int connections;
    private final int queryTimeoutSeconds;
    private final String loginEmail;
    private final LocalDate rangeStartDate;
    private final LocalDate rangeEndDate;
    private final int seasonId;

    public DevDbHotPathPrewarmService(
            @Qualifier("primaryDataSource") DataSource primaryDataSource,
            @Qualifier("stadiumDataSource") DataSource stadiumDataSource,
            @Value("${app.dev-db.hot-path-prewarm.enabled:false}") boolean enabled,
            @Value("${app.dev-db.hot-path-prewarm.connections:1}") int connections,
            @Value("${app.dev-db.hot-path-prewarm.timeout-ms:30000}") long timeoutMs,
            @Value("${app.dev-db.hot-path-prewarm.login-email:latency-prewarm@example.invalid}") String loginEmail,
            @Value("${app.dev-db.hot-path-prewarm.range-start-date:2026-06-18}") LocalDate rangeStartDate,
            @Value("${app.dev-db.hot-path-prewarm.range-end-date:2026-06-24}") LocalDate rangeEndDate,
            @Value("${app.dev-db.hot-path-prewarm.season-id:2026}") int seasonId) {
        this.primaryDataSource = primaryDataSource;
        this.stadiumDataSource = stadiumDataSource;
        this.enabled = enabled;
        this.connections = Math.min(Math.max(1, connections), MAX_CONNECTIONS_PER_DATASOURCE);
        this.queryTimeoutSeconds = Math.max(1, (int) Math.ceil(timeoutMs / 1000.0d));
        this.loginEmail = loginEmail;
        this.rangeStartDate = rangeStartDate;
        this.rangeEndDate = rangeEndDate;
        this.seasonId = seasonId;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        runPrewarm();
    }

    void runPrewarm() {
        if (!enabled) {
            return;
        }

        long startedAt = System.nanoTime();
        try {
            warmDataSource(
                    "primary",
                    primaryDataSource,
                    List.of(new PrewarmQuery("users-email", USER_EMAIL_SQL, statement -> {
                        statement.setString(1, loginEmail);
                    })));
            warmDataSource(
                    "stadium",
                    stadiumDataSource,
                    List.of(
                            new PrewarmQuery("game-range", GAME_RANGE_SQL, statement -> {
                                statement.setDate(1, java.sql.Date.valueOf(rangeStartDate));
                                statement.setDate(2, java.sql.Date.valueOf(rangeEndDate));
                            }),
                            new PrewarmQuery("kbo-season", SEASON_SQL, statement -> {
                                statement.setInt(1, seasonId);
                            })));
        } catch (RuntimeException ex) {
            log.warn("event=dev_db_hot_path_prewarm_failed elapsedMs={} reason={}",
                    elapsedMillis(startedAt),
                    ex.getMessage());
        } finally {
            log.info("event=dev_db_hot_path_prewarm_completed enabled=true elapsedMs={} connectionsPerDatasource={}",
                    elapsedMillis(startedAt),
                    connections);
        }
    }

    private void warmDataSource(String dataSourceName, DataSource dataSource, List<PrewarmQuery> queries) {
        List<Connection> borrowedConnections = borrowConnections(dataSourceName, dataSource);
        try {
            for (int connectionIndex = 0; connectionIndex < borrowedConnections.size(); connectionIndex++) {
                Connection connection = borrowedConnections.get(connectionIndex);
                for (PrewarmQuery query : queries) {
                    executePrewarmQuery(dataSourceName, connectionIndex + 1, connection, query);
                }
            }
        } finally {
            for (Connection connection : borrowedConnections) {
                closeQuietly(connection);
            }
        }
    }

    private List<Connection> borrowConnections(String dataSourceName, DataSource dataSource) {
        List<Connection> borrowedConnections = new ArrayList<>();
        for (int index = 1; index <= connections; index++) {
            Connection connection = null;
            try {
                connection = dataSource.getConnection();
                connection.setReadOnly(true);
                borrowedConnections.add(connection);
            } catch (SQLException ex) {
                closeQuietly(connection);
                log.warn("event=dev_db_hot_path_prewarm_connection_failed datasource={} connectionIndex={} reason={}",
                        dataSourceName,
                        index,
                        ex.getMessage());
            }
        }
        return borrowedConnections;
    }

    private void executePrewarmQuery(
            String dataSourceName,
            int connectionIndex,
            Connection connection,
            PrewarmQuery query) {
        long startedAt = System.nanoTime();
        try (PreparedStatement statement = connection.prepareStatement(query.sql())) {
            statement.setQueryTimeout(queryTimeoutSeconds);
            query.binder().bind(statement);
            int rows = 0;
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows++;
                }
            }
            log.info("event=dev_db_hot_path_prewarm_query_completed datasource={} connectionIndex={} query={} rows={} elapsedMs={}",
                    dataSourceName,
                    connectionIndex,
                    query.name(),
                    rows,
                    elapsedMillis(startedAt));
        } catch (SQLException ex) {
            log.warn("event=dev_db_hot_path_prewarm_query_failed datasource={} connectionIndex={} query={} elapsedMs={} reason={}",
                    dataSourceName,
                    connectionIndex,
                    query.name(),
                    elapsedMillis(startedAt),
                    ex.getMessage());
        }
    }

    private void closeQuietly(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException ex) {
            log.warn("event=dev_db_hot_path_prewarm_connection_close_failed reason={}", ex.getMessage());
        }
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }

    private record PrewarmQuery(String name, String sql, SqlBinder binder) {
    }

    @FunctionalInterface
    private interface SqlBinder {

        void bind(PreparedStatement statement) throws SQLException;
    }
}
