package com.example.mate.service;

import com.example.mate.dto.SellerPayoutProfileDTO;
import com.example.mate.entity.SellerPayoutProfile;
import com.example.mate.repository.SellerPayoutProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SellerPayoutProfileService {

    private final SellerPayoutProfileRepository sellerPayoutProfileRepository;

    @Transactional
    public SellerPayoutProfile upsert(SellerPayoutProfileDTO.UpsertRequest request) {
        if (request == null || request.getUserId() == null) {
            throw new IllegalArgumentException("userId는 필수입니다.");
        }
        String provider = normalizeProvider(request.getProvider());
        String providerSellerId = normalizeProviderSellerId(request.getProviderSellerId());

        sellerPayoutProfileRepository.findByProviderAndProviderSellerId(provider, providerSellerId)
                .filter(existing -> !existing.getUserId().equals(request.getUserId()))
                .ifPresent(existing -> {
                    throw new IllegalStateException("이미 다른 사용자에게 연결된 providerSellerId입니다.");
                });

        SellerPayoutProfile profile = sellerPayoutProfileRepository
                .findByUserIdAndProviderForUpdate(request.getUserId(), provider)
                .orElseGet(() -> SellerPayoutProfile.builder()
                        .userId(request.getUserId())
                        .provider(provider)
                        .build());

        profile.setProviderSellerId(providerSellerId);
        profile.setKycStatus(request.getKycStatus());
        profile.setMetadataJson(request.getMetadataJson());
        return sellerPayoutProfileRepository.save(profile);
    }

    @Transactional(readOnly = true)
    public Optional<SellerPayoutProfile> findByUserIdAndProvider(Long userId, String provider) {
        if (userId == null) {
            return Optional.empty();
        }
        return sellerPayoutProfileRepository.findByUserIdAndProvider(userId, normalizeProvider(provider));
    }

    @Transactional(readOnly = true, noRollbackFor = RuntimeException.class)
    public String getRequiredProviderSellerId(Long userId, String provider) {
        return findByUserIdAndProvider(userId, provider)
                .map(SellerPayoutProfile::getProviderSellerId)
                .filter(value -> value != null && !value.isBlank())
                .orElseThrow(() -> new IllegalStateException("SELLER_PROFILE_MISSING"));
    }

    private String normalizeProvider(String provider) {
        String normalized = provider == null ? "TOSS" : provider.trim().toUpperCase(Locale.ROOT);
        if (!"SIM".equals(normalized) && !"TOSS".equals(normalized)) {
            throw new IllegalArgumentException("지원되지 않는 provider입니다: " + provider);
        }
        return normalized;
    }

    private String normalizeProviderSellerId(String providerSellerId) {
        if (providerSellerId == null || providerSellerId.isBlank()) {
            throw new IllegalArgumentException("providerSellerId는 필수입니다.");
        }
        return providerSellerId.trim();
    }
}
