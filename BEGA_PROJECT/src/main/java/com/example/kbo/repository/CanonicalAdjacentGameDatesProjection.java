package com.example.kbo.repository;

import java.time.LocalDate;

public interface CanonicalAdjacentGameDatesProjection {

    LocalDate getPrevDate();

    LocalDate getNextDate();
}
