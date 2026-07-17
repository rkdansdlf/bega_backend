package com.example.mate.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class MatePaymentModeServiceTest {

    @Test
    void directTrade_exposesNoPaymentEnvironmentAndNoSellingPaymentRequirement() {
        MatePaymentModeService service = configured("DIRECT_TRADE", "TEST", true, false);

        assertThat(service.businessMode()).isEqualTo("DIRECT_TRADE");
        assertThat(service.paymentEnvironment()).isEqualTo("NONE");
        assertThat(service.isTossPaymentEnabled()).isFalse();
        assertThat(service.isSellingPaymentRequired()).isFalse();
    }

    @Test
    void inAppPayment_separatesBusinessModeFromProviderEnvironment() {
        MatePaymentModeService service = configured("IN_APP_PAYMENT", "LIVE", true, false);

        assertThat(service.businessMode()).isEqualTo("IN_APP_PAYMENT");
        assertThat(service.paymentProvider()).isEqualTo("TOSS");
        assertThat(service.paymentEnvironment()).isEqualTo("LIVE");
        assertThat(service.isTossPaymentEnabled()).isTrue();
        assertThat(service.isSellingPaymentRequired()).isTrue();
    }

    @Test
    void legacyTossTest_modeRemainsTestEnvironment() {
        MatePaymentModeService service = configured("TOSS_TEST", "LIVE", true, false);

        assertThat(service.businessMode()).isEqualTo("IN_APP_PAYMENT");
        assertThat(service.paymentEnvironment()).isEqualTo("TEST");
    }

    @Test
    void inAppPayment_withUnsupportedProvider_disablesTossEndpoints() {
        MatePaymentModeService service = configured("IN_APP_PAYMENT", "PAYCO", "LIVE", true, false);

        assertThat(service.businessMode()).isEqualTo("IN_APP_PAYMENT");
        assertThat(service.paymentProvider()).isEqualTo("UNSUPPORTED");
        assertThat(service.isTossPaymentEnabled()).isFalse();
        assertThat(service.isSellingPaymentRequired()).isTrue();
    }

    private MatePaymentModeService configured(
            String mode,
            String environment,
            boolean sellingEnforced,
            boolean payoutEnabled) {
        return configured(mode, "TOSS", environment, sellingEnforced, payoutEnabled);
    }

    private MatePaymentModeService configured(
            String mode,
            String provider,
            String environment,
            boolean sellingEnforced,
            boolean payoutEnabled) {
        MatePaymentModeService service = new MatePaymentModeService();
        ReflectionTestUtils.setField(service, "configuredMode", mode);
        ReflectionTestUtils.setField(service, "configuredProvider", provider);
        ReflectionTestUtils.setField(service, "configuredEnvironment", environment);
        ReflectionTestUtils.setField(service, "paymentSellingEnforced", sellingEnforced);
        ReflectionTestUtils.setField(service, "paymentPayoutEnabled", payoutEnabled);
        ReflectionTestUtils.setField(service, "paymentPayoutProvider", "SIM");
        return service;
    }
}
