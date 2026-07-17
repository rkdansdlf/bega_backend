package com.example.mate.service.payout;

import com.example.mate.config.TossPayoutConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class TossPayoutGatewayTest {

    @Test
    void requestPayoutSendsStableProviderReferenceAndIdempotencyKey() {
        TossPayoutConfig config = new TossPayoutConfig();
        config.setApiSecret("test-secret");
        config.setBaseUrl("https://api.tosspayments.com");
        config.setSecurityMode("PLAIN");

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();
        ObjectMapper objectMapper = new ObjectMapper();
        TossPayoutGateway gateway = new TossPayoutGateway(
                config,
                restClient,
                new TossPayoutEncryptionService(objectMapper),
                objectMapper);

        server.expect(once(), requestTo("https://api.tosspayments.com/v2/payouts"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Idempotency-Key", "mate-payout-98"))
                .andExpect(content().string(containsString("\"refPayoutId\":\"mate-payout-98\"")))
                .andExpect(content().string(containsString("\"paymentTransactionId\":98")))
                .andRespond(withSuccess(
                        "{\"payoutId\":\"provider-98\",\"status\":\"REQUESTED\"}",
                        MediaType.APPLICATION_JSON));

        PayoutGateway.PayoutResult result = gateway.requestPayout(new PayoutGateway.PayoutRequest(
                98L,
                "ORDER-98",
                124L,
                "seller-124",
                12000,
                "KRW",
                "mate-payout-98"));

        assertThat(result.providerRef()).isEqualTo("provider-98");
        server.verify();
    }

    @Test
    void payoutStatusHttpFailureRemainsUnknownUntilExplicitProviderOutcome() {
        TossPayoutConfig config = new TossPayoutConfig();
        config.setApiSecret("test-secret");
        config.setBaseUrl("https://api.tosspayments.com");
        config.setSecurityMode("PLAIN");

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();
        ObjectMapper objectMapper = new ObjectMapper();
        TossPayoutGateway gateway = new TossPayoutGateway(
                config,
                restClient,
                new TossPayoutEncryptionService(objectMapper),
                objectMapper);
        server.expect(once(), requestTo("https://api.tosspayments.com/v2/payouts/provider-500"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("{\"code\":\"PROVIDER_TEMPORARY_ERROR\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        PayoutGateway.PayoutStatusResult result = gateway.getPayoutStatus("provider-500");

        assertThat(result.rawStatus()).isEqualTo("UNKNOWN");
        assertThat(result.failureCode()).isEqualTo("PROVIDER_TEMPORARY_ERROR");
        server.verify();
    }
}
