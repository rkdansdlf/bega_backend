package com.example.common.clienterror;

public enum ClientErrorBucket {
    API("api"),
    RUNTIME("runtime"),
    FEEDBACK("feedback");

    private final String value;

    ClientErrorBucket(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static ClientErrorBucket fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
        for (ClientErrorBucket bucket : values()) {
            if (bucket.value.equals(normalized)) {
                return bucket;
            }
        }
        return null;
    }
}
