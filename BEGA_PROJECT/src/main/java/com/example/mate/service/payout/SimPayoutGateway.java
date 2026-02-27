package com.example.mate.service.payout;

import org.springframework.stereotype.Component;

@Component
public class SimPayoutGateway implements PayoutGateway {

    @Override
    public String getProviderCode() {
        return "SIM";
    }

    @Override
    public PayoutResult requestPayout(PayoutRequest request) {
        return new PayoutResult("SIM-" + request.paymentTransactionId(), "COMPLETED");
    }

    @Override
    public SellerRegistrationResult registerSeller(SellerRegistrationRequest request) {
        return new SellerRegistrationResult(request.providerSellerId(), "REGISTERED");
    }
}
