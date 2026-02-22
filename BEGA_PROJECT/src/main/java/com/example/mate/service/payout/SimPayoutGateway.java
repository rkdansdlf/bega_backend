package com.example.mate.service.payout;

import com.example.mate.entity.PaymentTransaction;
import org.springframework.stereotype.Component;

@Component
public class SimPayoutGateway implements PayoutGateway {

    @Override
    public String getProviderCode() {
        return "SIM";
    }

    @Override
    public String requestPayout(PaymentTransaction paymentTransaction) {
        return "SIM-" + paymentTransaction.getId();
    }
}
