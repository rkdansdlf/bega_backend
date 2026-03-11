package com.example.mate.dto;

import com.example.mate.entity.PartyApplication;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PartyApplicationDtoSecurityTest {

    @Test
    void responseFrom_doesNotExposePaymentKeyOrOrderId() {
        PartyApplication application = PartyApplication.builder()
                .id(7L)
                .partyId(8L)
                .applicantId(9L)
                .applicantName("applicant")
                .depositAmount(30000)
                .paymentType(PartyApplication.PaymentType.FULL)
                .paymentKey("pay-secret")
                .orderId("ORDER-SECRET")
                .isPaid(true)
                .isApproved(false)
                .isRejected(false)
                .build();

        PartyApplicationDTO.Response response = PartyApplicationDTO.Response.from(application);

        assertThat(response.getPaymentKey()).isNull();
        assertThat(response.getOrderId()).isNull();
    }
}
