package com.example.kbo.repository;

import java.time.LocalDate;

public interface CanonicalGameDateBoundsProjection {

    LocalDate getEarliestGameDate();

    LocalDate getLatestGameDate();
}
