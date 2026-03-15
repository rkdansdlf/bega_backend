package com.example.common.clienterror;

import java.util.Locale;

public enum ClientErrorSource {
    API("api", ClientErrorBucket.API),
    RUNTIME("runtime", ClientErrorBucket.RUNTIME),
    UNHANDLED_REJECTION("unhandled_rejection", ClientErrorBucket.RUNTIME),
    UNKNOWN("unknown", ClientErrorBucket.RUNTIME);

    private final String value;
    private final ClientErrorBucket bucket;

    ClientErrorSource(String value, ClientErrorBucket bucket) {
        this.value = value;
        this.bucket = bucket;
    }

    public String getValue() {
        return value;
    }

    public ClientErrorBucket getBucket() {
        return bucket;
    }

    public static ClientErrorSource fromCategory(String category) {
        if (category == null || category.isBlank()) {
            return UNKNOWN;
        }

        String normalized = category.trim().toLowerCase(Locale.ROOT);
        for (ClientErrorSource source : values()) {
            if (source.value.equals(normalized)) {
                return source;
            }
        }
        return UNKNOWN;
    }
}
