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

    @PostConstruct
    public void logPaymentModeConfiguration() {
        log.info(
                "[MatePaymentMode] configuredMode={}, resolvedMode={}, payment.selling.enforced={}, payment.payout.enabled={}, payment.payout.provider={}",
                configuredMode,
                currentMode(),
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

    public boolean isTossTest() {
        return currentMode() == MatePaymentMode.TOSS_TEST;
    }
}
