package com.example.common.clienterror;

import java.util.Locale;

public enum ClientErrorAlertChannel {
    TELEGRAM("telegram"),
    SLACK("slack");

    private final String value;

    ClientErrorAlertChannel(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static ClientErrorAlertChannel fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (ClientErrorAlertChannel channel : values()) {
            if (channel.value.equals(normalized) || channel.name().equalsIgnoreCase(normalized)) {
                return channel;
            }
        }
        return null;
    }
}
