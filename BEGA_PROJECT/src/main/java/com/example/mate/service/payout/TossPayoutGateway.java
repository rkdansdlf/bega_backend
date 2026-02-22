package com.example.mate.service.payout;

import com.example.mate.entity.PaymentTransaction;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "payment.payout", name = "provider", havingValue = "TOSS")
public class TossPayoutGateway implements PayoutGateway {

    @Override
    public String getProviderCode() {
        return "TOSS";
    }

    @Override
    public String requestPayout(PaymentTransaction paymentTransaction) {
        throw new UnsupportedOperationException("TOSS payout provider is not implemented yet");
    }

    @Override
    public String requestPayoutStatus(PaymentTransaction paymentTransaction) {
        return "SKIPPED";
    }
}
