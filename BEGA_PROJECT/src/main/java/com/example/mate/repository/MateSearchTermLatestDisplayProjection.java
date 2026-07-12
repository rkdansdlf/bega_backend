package com.example.mate.repository;

import java.time.Instant;

public interface MateSearchTermLatestDisplayProjection {

    String getNormalizedTerm();

    String getDisplayTerm();

    Instant getLastSearchedAt();

    Long getId();
}
