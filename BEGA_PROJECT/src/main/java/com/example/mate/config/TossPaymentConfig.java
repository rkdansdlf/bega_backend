package com.example.mate.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@ConfigurationProperties(prefix = "toss.payment")
public class TossPaymentConfig {

    private String secretKey;
    private String confirmUrl = "https://api.tosspayments.com/v1/payments/confirm";
    private String cancelUrl = "https://api.tosspayments.com/v1/payments/{paymentKey}/cancel";

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

    @Bean
    public RestClient tossRestClient(RestClient.Builder builder) {
        return builder.build();
    }
}
