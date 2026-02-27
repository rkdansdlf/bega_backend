package com.example.mate.service;

import com.example.mate.dto.SellerPayoutProfileDTO;
import com.example.mate.entity.SellerPayoutProfile;
import com.example.mate.repository.SellerPayoutProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class SellerPayoutProfileServiceTest {

    @Mock
    private SellerPayoutProfileRepository sellerPayoutProfileRepository;

    @InjectMocks
    private SellerPayoutProfileService sellerPayoutProfileService;

    @Test
    void upsert_createsNewProfile() {
        SellerPayoutProfileDTO.UpsertRequest request = SellerPayoutProfileDTO.UpsertRequest.builder()
                .userId(10L)
                .provider("toss")
                .providerSellerId("seller_10")
                .kycStatus("APPROVED")
                .metadataJson("{\"tier\":\"A\"}")
                .build();

        given(sellerPayoutProfileRepository.findByProviderAndProviderSellerId("TOSS", "seller_10"))
                .willReturn(Optional.empty());
        given(sellerPayoutProfileRepository.findByUserIdAndProviderForUpdate(10L, "TOSS"))
                .willReturn(Optional.empty());
        given(sellerPayoutProfileRepository.save(any(SellerPayoutProfile.class)))
                .willAnswer(inv -> inv.getArgument(0));

        SellerPayoutProfile profile = sellerPayoutProfileService.upsert(request);

        assertThat(profile.getUserId()).isEqualTo(10L);
        assertThat(profile.getProvider()).isEqualTo("TOSS");
        assertThat(profile.getProviderSellerId()).isEqualTo("seller_10");
    }

    @Test
    void getRequiredProviderSellerId_throwsWhenProfileMissing() {
        given(sellerPayoutProfileRepository.findByUserIdAndProvider(10L, "TOSS"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> sellerPayoutProfileService.getRequiredProviderSellerId(10L, "TOSS"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SELLER_PROFILE_MISSING");
    }
}

