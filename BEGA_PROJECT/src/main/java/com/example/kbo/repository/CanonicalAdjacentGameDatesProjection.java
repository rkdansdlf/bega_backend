package com.example.kbo.repository;

import java.time.LocalDate;

/**
 * Date-only projection over canonical game_date values.
 */
public interface CanonicalAdjacentGameDatesProjection {

    LocalDate getPrevDate();

    LocalDate getNextDate();
}
