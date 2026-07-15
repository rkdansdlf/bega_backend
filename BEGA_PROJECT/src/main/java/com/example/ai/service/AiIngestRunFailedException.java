package com.example.ai.service;

public class AiIngestRunFailedException extends RuntimeException {

    private static final String DEFAULT_CODE = "INGEST_RUN_FAILED";

    private final String errorCode;

    public AiIngestRunFailedException(String errorCode) {
        super("AI ingestion run failed: " + sanitizeCode(errorCode));
        this.errorCode = sanitizeCode(errorCode);
    }

    public String getErrorCode() {
        return errorCode;
    }

    private static String sanitizeCode(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_CODE;
        }
        String sanitized = value.trim().toUpperCase().replaceAll("[^A-Z0-9_]", "_");
        if (sanitized.isBlank()) {
            return DEFAULT_CODE;
        }
        return sanitized.substring(0, Math.min(sanitized.length(), 96));
    }
}
