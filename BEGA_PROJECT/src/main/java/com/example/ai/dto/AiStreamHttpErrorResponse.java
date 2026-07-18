package com.example.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record AiStreamHttpErrorResponse(
        String code,
        String message,
        String detail,
        boolean retryable,
        @JsonProperty("retry_after_seconds") Long retryAfterSeconds,
        @JsonProperty("supported_versions") List<String> supportedVersions) {

    public AiStreamHttpErrorResponse {
        supportedVersions = supportedVersions == null ? List.of() : List.copyOf(supportedVersions);
    }
}
