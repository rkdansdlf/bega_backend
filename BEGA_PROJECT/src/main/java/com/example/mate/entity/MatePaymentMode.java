package com.example.mate.entity;

import java.util.Locale;

public enum MatePaymentMode {
    DIRECT_TRADE,
    /**
     * Legacy name kept for existing test/deployment configuration.
     * The public capability contract exposes this as IN_APP_PAYMENT + TEST.
     */
    TOSS_TEST,
    IN_APP_PAYMENT;

    public boolean isInAppPayment() {
        return this != DIRECT_TRADE;
    }

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
