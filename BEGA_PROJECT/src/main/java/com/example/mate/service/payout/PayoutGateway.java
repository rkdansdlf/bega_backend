package com.example.mate.service.payout;

import com.example.mate.entity.PaymentTransaction;

public interface PayoutGateway {

    String getProviderCode();

    String requestPayout(PaymentTransaction paymentTransaction);

    default String requestPayoutStatus(PaymentTransaction paymentTransaction) {
        return null;
    }
}
