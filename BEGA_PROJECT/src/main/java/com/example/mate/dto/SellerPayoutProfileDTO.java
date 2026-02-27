package com.example.mate.dto;

import com.example.mate.entity.SellerPayoutProfile;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

public class SellerPayoutProfileDTO {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UpsertRequest {
        private Long userId;
        @Builder.Default
        private String provider = "TOSS";
        private String providerSellerId;
        private String kycStatus;
        private String metadataJson;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        private Long id;
        private Long userId;
        private String provider;
        private String providerSellerId;
        private String kycStatus;
        private String metadataJson;
        private Instant createdAt;
        private Instant updatedAt;

        public static Response from(SellerPayoutProfile profile) {
            return Response.builder()
                    .id(profile.getId())
                    .userId(profile.getUserId())
                    .provider(profile.getProvider())
                    .providerSellerId(profile.getProviderSellerId())
                    .kycStatus(profile.getKycStatus())
                    .metadataJson(profile.getMetadataJson())
                    .createdAt(profile.getCreatedAt())
                    .updatedAt(profile.getUpdatedAt())
                    .build();
        }
    }
}

