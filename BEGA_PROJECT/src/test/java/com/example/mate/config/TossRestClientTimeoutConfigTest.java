package com.example.mate.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class TossRestClientTimeoutConfigTest {

    @Test
    void paymentClient_appliesConfiguredConnectAndReadTimeouts() {
        TossPaymentConfig config = new TossPaymentConfig();
        config.setConnectTimeoutMillis(1_234);
        config.setReadTimeoutMillis(5_678);

        RestClient client = config.tossRestClient(RestClient.builder());

        assertTimeouts(client, 1_234, 5_678);
    }

    @Test
    void payoutClient_appliesConfiguredConnectAndReadTimeouts() {
        TossPayoutConfig config = new TossPayoutConfig();
        config.setConnectTimeoutMillis(2_345);
        config.setReadTimeoutMillis(6_789);

        RestClient client = config.tossPayoutRestClient(RestClient.builder());

        assertTimeouts(client, 2_345, 6_789);
    }

    @Test
    void timeoutConfiguration_rejectsUnboundedOrExcessiveValues() {
        TossPaymentConfig paymentConfig = new TossPaymentConfig();
        TossPayoutConfig payoutConfig = new TossPayoutConfig();

        assertThatIllegalArgumentException().isThrownBy(() -> paymentConfig.setConnectTimeoutMillis(0));
        assertThatIllegalArgumentException().isThrownBy(() -> paymentConfig.setReadTimeoutMillis(60_001));
        assertThatIllegalArgumentException().isThrownBy(() -> payoutConfig.setConnectTimeoutMillis(0));
        assertThatIllegalArgumentException().isThrownBy(() -> payoutConfig.setReadTimeoutMillis(60_001));
    }

    private void assertTimeouts(RestClient client, int connectTimeout, int readTimeout) {
        Object requestFactory = ReflectionTestUtils.getField(client, "clientRequestFactory");
        assertThat(requestFactory).isInstanceOf(SimpleClientHttpRequestFactory.class);
        assertThat(ReflectionTestUtils.getField(requestFactory, "connectTimeout")).isEqualTo(connectTimeout);
        assertThat(ReflectionTestUtils.getField(requestFactory, "readTimeout")).isEqualTo(readTimeout);
    }
}
