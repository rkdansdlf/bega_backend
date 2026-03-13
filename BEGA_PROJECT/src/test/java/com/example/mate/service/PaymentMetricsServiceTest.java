package com.example.mate.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentMetricsServiceTest {

    @Test
    void recordCompensationRequested_incrementsDedicatedCounterOnly() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        PaymentMetricsService metricsService = new PaymentMetricsService(meterRegistry);

        metricsService.recordCompensationRequested();

        double requested = meterRegistry.counter("mate_payment_compensation_requested_total").count();
        double success = meterRegistry.counter("mate_payment_compensation_total", "result", "success").count();
        double fail = meterRegistry.counter("mate_payment_compensation_total", "result", "fail").count();

        assertThat(requested).isEqualTo(1.0d);
        assertThat(success).isZero();
        assertThat(fail).isZero();
    }

    @Test
    void recordCompensation_tracksOnlySuccessAndFail() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        PaymentMetricsService metricsService = new PaymentMetricsService(meterRegistry);

        metricsService.recordCompensation("success");
        metricsService.recordCompensation("fail");
        metricsService.recordCompensation("retry");

        double success = meterRegistry.counter("mate_payment_compensation_total", "result", "success").count();
        double fail = meterRegistry.counter("mate_payment_compensation_total", "result", "fail").count();

        assertThat(success).isEqualTo(1.0d);
        assertThat(fail).isEqualTo(1.0d);
    }
}

