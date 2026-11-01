package com.example.mate.service;

import com.example.mate.dto.SellerPayoutProfileDTO;
import com.example.mate.entity.SellerPayoutProfile;
import com.example.mate.repository.SellerPayoutProfileRepository;
import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SellerPayoutProfileService {

    private static final int MAX_PROVIDER_SELLER_ID_LENGTH = 120;
    private static final int MAX_KYC_STATUS_LENGTH = 50;

    private final SellerPayoutProfileRepository sellerPayoutProfileRepository;
    private final UserRepository userRepository;

    @Transactional
    public SellerPayoutProfile upsert(SellerPayoutProfileDTO.UpsertRequest request) {
        if (request == null || request.getUserId() == null) {
            throw new IllegalArgumentException("userId는 필수입니다.");
        }
        UserEntity user = userRepository.findByIdForWrite(request.getUserId())
                .orElseThrow(() -> new IllegalStateException("PAYOUT_PROFILE_ACCOUNT_UNAVAILABLE"));
        if (!user.isEnabled() || user.isPendingDeletion()) {
            throw new IllegalStateException("PAYOUT_PROFILE_ACCOUNT_UNAVAILABLE");
        }
        String provider = normalizeProvider(request.getProvider());
        String providerSellerId = normalizeProviderSellerId(request.getProviderSellerId());
        if (providerSellerId.length() > MAX_PROVIDER_SELLER_ID_LENGTH) {
            throw new IllegalArgumentException("providerSellerId는 공백 제거 후 120자 이하여야 합니다.");
        }
        String kycStatus = request.getKycStatus();
        if (kycStatus != null && kycStatus.length() > MAX_KYC_STATUS_LENGTH) {
            throw new IllegalArgumentException("kycStatus는 50자 이하여야 합니다.");
        }

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
        profile.setKycStatus(kycStatus);
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
