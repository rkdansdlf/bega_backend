package com.example.mate.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MatePaymentModeTest {

    @Test
    void from_unknownMode_fallsBackToDirectTrade() {
        assertThat(MatePaymentMode.from("UNKNOWN")).isEqualTo(MatePaymentMode.DIRECT_TRADE);
        assertThat(MatePaymentMode.from(null)).isEqualTo(MatePaymentMode.DIRECT_TRADE);
        assertThat(MatePaymentMode.from("")).isEqualTo(MatePaymentMode.DIRECT_TRADE);
    }

    @Test
    void from_tossTestMode_parsesSuccessfully() {
        assertThat(MatePaymentMode.from("TOSS_TEST")).isEqualTo(MatePaymentMode.TOSS_TEST);
        assertThat(MatePaymentMode.from("toss_test")).isEqualTo(MatePaymentMode.TOSS_TEST);
    }
}
