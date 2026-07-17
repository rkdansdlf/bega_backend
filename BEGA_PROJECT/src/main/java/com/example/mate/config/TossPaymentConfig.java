package com.example.mate.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@ConfigurationProperties(prefix = "toss.payment")
public class TossPaymentConfig {

    private static final int MIN_TIMEOUT_MILLIS = 100;
    private static final int MAX_TIMEOUT_MILLIS = 60_000;

    private String secretKey;
    private String confirmUrl = "https://api.tosspayments.com/v1/payments/confirm";
    private String cancelUrl = "https://api.tosspayments.com/v1/payments/{paymentKey}/cancel";
    private int connectTimeoutMillis = 3_000;
    private int readTimeoutMillis = 10_000;

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getConfirmUrl() {
        return confirmUrl;
    }

    public void setConfirmUrl(String confirmUrl) {
        this.confirmUrl = confirmUrl;
    }

    public String getCancelUrl() {
        return cancelUrl;
    }

    public void setCancelUrl(String cancelUrl) {
        this.cancelUrl = cancelUrl;
    }

    public int getConnectTimeoutMillis() {
        return connectTimeoutMillis;
    }

    public void setConnectTimeoutMillis(int connectTimeoutMillis) {
        this.connectTimeoutMillis = requireBoundedTimeout("connectTimeoutMillis", connectTimeoutMillis);
    }

    public int getReadTimeoutMillis() {
        return readTimeoutMillis;
    }

    public void setReadTimeoutMillis(int readTimeoutMillis) {
        this.readTimeoutMillis = requireBoundedTimeout("readTimeoutMillis", readTimeoutMillis);
    }

    @Bean
    public RestClient tossRestClient(RestClient.Builder builder) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMillis);
        requestFactory.setReadTimeout(readTimeoutMillis);
        return builder.requestFactory(requestFactory).build();
    }

    private int requireBoundedTimeout(String property, int value) {
        if (value < MIN_TIMEOUT_MILLIS || value > MAX_TIMEOUT_MILLIS) {
            throw new IllegalArgumentException(
                    property + " must be between " + MIN_TIMEOUT_MILLIS + " and " + MAX_TIMEOUT_MILLIS);
        }
        return value;
    }
}
