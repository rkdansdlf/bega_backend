package com.example.kbo.validation;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ManualBaseballDataMissingItem(
        String key,
        String label,
        String reason,
        @JsonProperty("expected_format") String expectedFormat) {}
