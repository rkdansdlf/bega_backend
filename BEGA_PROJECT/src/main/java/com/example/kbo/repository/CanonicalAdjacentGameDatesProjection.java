package com.example.kbo.repository;

import java.time.LocalDateTime;

/**
 * Native-query projection over MAX/MIN(game_date). Declared as LocalDateTime,
 * not LocalDate: PostgreSQL's DATE column round-trips as LocalDate through
 * the JDBC driver, but Oracle's DATE type always carries a time component and
 * the ojdbc driver hands the aggregate back as java.sql.Timestamp — Spring
 * Data has no built-in Timestamp-to-LocalDate projection converter, so a
 * LocalDate-typed getter here throws at read time once the baseball
 * datasource is Oracle. Callers truncate to LocalDate themselves.
 */
public interface CanonicalAdjacentGameDatesProjection {

    LocalDateTime getPrevDate();

    LocalDateTime getNextDate();
}
