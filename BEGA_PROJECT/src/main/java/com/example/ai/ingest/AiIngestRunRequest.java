package com.example.ai.ingest;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record AiIngestRunRequest(
        List<String> tables,
        @JsonProperty("season_year") Integer seasonYear,
        String mode,
        @JsonProperty("trigger_source") String triggerSource) {

    public AiIngestRunRequest {
        tables = tables == null ? List.of() : List.copyOf(tables);
    }
}
