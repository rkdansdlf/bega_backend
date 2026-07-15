package com.example.ai.service;

public class AiIngestManualDataRequiredException extends RuntimeException {

    public static final String CONTRACT_CODE = "MANUAL_BASEBALL_DATA_REQUIRED";
    private static final String DEFAULT_OPERATOR_MESSAGE = "Operator-provided baseball data is required.";

    private final String contractCode;
    private final String operatorMessage;

    public AiIngestManualDataRequiredException(String operatorMessage) {
        super(CONTRACT_CODE + ": " + sanitizeMessage(operatorMessage));
        this.contractCode = CONTRACT_CODE;
        this.operatorMessage = sanitizeMessage(operatorMessage);
    }

    public String getContractCode() {
        return contractCode;
    }

    public String getOperatorMessage() {
        return operatorMessage;
    }

    private static String sanitizeMessage(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_OPERATOR_MESSAGE;
        }
        String sanitized = value.replaceAll("[\\r\\n\\t]+", " ")
                .replaceAll("(?i)(token|password|secret)\\s*[:=]\\s*\\S+", "$1=[REDACTED]")
                .replaceAll("https?://\\S+", "[REDACTED_URL]")
                .replaceAll("\\s+", " ")
                .trim();
        if (sanitized.isBlank()) {
            return DEFAULT_OPERATOR_MESSAGE;
        }
        return sanitized.substring(0, Math.min(sanitized.length(), 500));
    }
}
