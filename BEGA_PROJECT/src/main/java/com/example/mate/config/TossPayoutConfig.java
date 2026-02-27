package com.example.mate.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@ConfigurationProperties(prefix = "toss.payout")
public class TossPayoutConfig {

    private String apiSecret;
    private String baseUrl = "https://api.tosspayments.com";
    private String requestPath = "/v2/payouts";
    private String statusPath = "/v2/payouts/{payoutId}";
    private String sellerRegisterPath = "/v2/payouts/sellers";
    private String securityMode = "ENCRYPTION";
    private String encryptionPublicKey;
    private String encryptionPublicKeyPath;

    public String getApiSecret() {
        return apiSecret;
    }

    public void setApiSecret(String apiSecret) {
        this.apiSecret = apiSecret;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getRequestPath() {
        return requestPath;
    }

    public void setRequestPath(String requestPath) {
        this.requestPath = requestPath;
    }

    public String getStatusPath() {
        return statusPath;
    }

    public void setStatusPath(String statusPath) {
        this.statusPath = statusPath;
    }

    public String getSellerRegisterPath() {
        return sellerRegisterPath;
    }

    public void setSellerRegisterPath(String sellerRegisterPath) {
        this.sellerRegisterPath = sellerRegisterPath;
    }

    public String getSecurityMode() {
        return securityMode;
    }

    public void setSecurityMode(String securityMode) {
        this.securityMode = securityMode;
    }

    public String getEncryptionPublicKey() {
        return encryptionPublicKey;
    }

    public void setEncryptionPublicKey(String encryptionPublicKey) {
        this.encryptionPublicKey = encryptionPublicKey;
    }

    public String getEncryptionPublicKeyPath() {
        return encryptionPublicKeyPath;
    }

    public void setEncryptionPublicKeyPath(String encryptionPublicKeyPath) {
        this.encryptionPublicKeyPath = encryptionPublicKeyPath;
    }

    @Bean
    public RestClient tossPayoutRestClient(RestClient.Builder builder) {
        return builder.build();
    }
}

