package com.example.kbo.repository;

import java.time.LocalDateTime;

/**
 * Native-query projection over MIN/MAX(game_date). See
 * CanonicalAdjacentGameDatesProjection for why this is LocalDateTime and not
 * LocalDate — Oracle's DATE type always carries a time component, and the
 * ojdbc driver's default Timestamp mapping has no automatic Spring Data
 * projection converter down to LocalDate.
 */
public interface CanonicalGameDateBoundsProjection {

    LocalDateTime getEarliestGameDate();

    LocalDateTime getLatestGameDate();
}
