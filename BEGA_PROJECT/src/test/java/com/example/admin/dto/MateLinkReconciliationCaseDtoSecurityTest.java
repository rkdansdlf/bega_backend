package com.example.admin.dto;

import com.example.mate.entity.MateLinkReconciliationReasonCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class MateLinkReconciliationCaseDtoSecurityTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void caseResponseContainsOnlyInternalIdentifiersReasonAndObservationTime() throws Exception {
        String json = objectMapper.writeValueAsString(new MateLinkReconciliationCaseDto(
                1L,
                2L,
                3L,
                4L,
                MateLinkReconciliationReasonCode.PAYMENT_TRANSACTION_MISSING,
                Instant.parse("2026-10-14T00:00:00Z")));

        assertThat(json).contains(
                "\"id\":1",
                "\"partyId\":2",
                "\"applicationId\":3",
                "\"paymentTransactionId\":4",
                "\"reasonCode\":\"PAYMENT_TRANSACTION_MISSING\"");
        assertThat(json).doesNotContain(
                "email", "phone", "name", "paymentKey", "orderId", "providerRef", "providerPayload");
    }
}
