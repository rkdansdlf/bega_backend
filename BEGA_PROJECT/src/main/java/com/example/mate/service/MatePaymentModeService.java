package com.example.mate.service;

import com.example.mate.entity.MatePaymentMode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class MatePaymentModeService {

    @Value("${mate.payment.mode:DIRECT_TRADE}")
    private String configuredMode;

    @Value("${payment.selling.enforced:true}")
    private boolean paymentSellingEnforced;

    @Value("${payment.payout.enabled:false}")
    private boolean paymentPayoutEnabled;

    @Value("${payment.payout.provider:SIM}")
    private String paymentPayoutProvider;

    @Value("${mate.payment.provider:TOSS}")
    private String configuredProvider;

    @Value("${mate.payment.environment:TEST}")
    private String configuredEnvironment;

    @PostConstruct
    public void logPaymentModeConfiguration() {
        log.info(
                "[MatePaymentMode] configuredMode={}, resolvedMode={}, businessMode={}, provider={}, environment={}, payment.selling.enforced={}, payment.payout.enabled={}, payment.payout.provider={}",
                configuredMode,
                currentMode(),
                businessMode(),
                paymentProvider(),
                paymentEnvironment(),
                paymentSellingEnforced,
                paymentPayoutEnabled,
                paymentPayoutProvider);
    }

    public MatePaymentMode currentMode() {
        return MatePaymentMode.from(configuredMode);
    }

    public boolean isDirectTrade() {
        return currentMode() == MatePaymentMode.DIRECT_TRADE;
    }

    public boolean isInAppPayment() {
        return !isDirectTrade();
    }

    public boolean isTossTest() {
        return currentMode() == MatePaymentMode.TOSS_TEST;
    }

    public String businessMode() {
        return isInAppPayment() ? "IN_APP_PAYMENT" : "DIRECT_TRADE";
    }

    public String paymentMode() {
        return currentMode().name();
    }

    public String paymentProvider() {
        return normalize(configuredProvider, "TOSS");
    }

    public String paymentEnvironment() {
        if (isDirectTrade()) {
            return "NONE";
        }
        if (isTossTest()) {
            return "TEST";
        }
        return normalizeEnvironment(configuredEnvironment);
    }

    public boolean isTossPaymentEnabled() {
        return isInAppPayment();
    }

    public boolean isSellingPaymentRequired() {
        return isInAppPayment() && paymentSellingEnforced;
    }

    public boolean isPayoutEnabled() {
        return paymentPayoutEnabled;
    }

    public String payoutProvider() {
        return normalize(paymentPayoutProvider, "SIM");
    }

    private String normalize(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private String normalizeEnvironment(String value) {
        String normalized = normalize(value, "TEST");
        return "LIVE".equals(normalized) ? "LIVE" : "TEST";
    }
}
