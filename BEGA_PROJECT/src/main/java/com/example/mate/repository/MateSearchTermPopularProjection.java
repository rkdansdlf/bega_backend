package com.example.mate.repository;

import java.time.Instant;

public interface MateSearchTermPopularProjection {

    String getNormalizedTerm();

    Long getSearchCount();

    Instant getLastSearchedAt();
}
