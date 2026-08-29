package com.example.kbo.repository;

import java.time.LocalDate;

/**
 * Date-only projection over canonical game_date bounds.
 */
public interface CanonicalGameDateBoundsProjection {

    LocalDate getEarliestGameDate();

    LocalDate getLatestGameDate();
}
