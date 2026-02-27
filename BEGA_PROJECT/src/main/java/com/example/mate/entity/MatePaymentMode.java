package com.example.mate.entity;

import java.util.Locale;

public enum MatePaymentMode {
    DIRECT_TRADE,
    TOSS_TEST;

    public static MatePaymentMode from(String rawMode) {
        if (rawMode == null || rawMode.isBlank()) {
            return DIRECT_TRADE;
        }
        try {
            return MatePaymentMode.valueOf(rawMode.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return DIRECT_TRADE;
        }
    }
}
