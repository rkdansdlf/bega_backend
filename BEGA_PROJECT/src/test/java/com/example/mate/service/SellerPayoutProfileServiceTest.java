package com.example.mate.service;

import com.example.mate.dto.SellerPayoutProfileDTO;
import com.example.mate.entity.SellerPayoutProfile;
import com.example.mate.repository.SellerPayoutProfileRepository;
import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.inOrder;

@ExtendWith(MockitoExtension.class)
class SellerPayoutProfileServiceTest {

    @Mock
    private SellerPayoutProfileRepository sellerPayoutProfileRepository;

    @Mock
    private UserRepository userRepository;

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
        given(userRepository.findByIdForWrite(10L)).willReturn(Optional.of(UserEntity.builder()
                .id(10L).enabled(true).pendingDeletion(false).build()));
        given(sellerPayoutProfileRepository.findByUserIdAndProviderForUpdate(10L, "TOSS"))
                .willReturn(Optional.empty());
        given(sellerPayoutProfileRepository.save(any(SellerPayoutProfile.class)))
                .willAnswer(inv -> inv.getArgument(0));

        SellerPayoutProfile profile = sellerPayoutProfileService.upsert(request);

        assertThat(profile.getUserId()).isEqualTo(10L);
        assertThat(profile.getProvider()).isEqualTo("TOSS");
        assertThat(profile.getProviderSellerId()).isEqualTo("seller_10");
        InOrder lockOrder = inOrder(userRepository, sellerPayoutProfileRepository);
        lockOrder.verify(userRepository).findByIdForWrite(10L);
        lockOrder.verify(sellerPayoutProfileRepository).findByProviderAndProviderSellerId("TOSS", "seller_10");
        lockOrder.verify(sellerPayoutProfileRepository).findByUserIdAndProviderForUpdate(10L, "TOSS");
    }

    @Test
    void getRequiredProviderSellerId_throwsWhenProfileMissing() {
        given(sellerPayoutProfileRepository.findByUserIdAndProvider(10L, "TOSS"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> sellerPayoutProfileService.getRequiredProviderSellerId(10L, "TOSS"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SELLER_PROFILE_MISSING");
    }

    @Test
    void upsert_rejectsDisabledOrDeletingAccountBeforeReadingOrWritingPayoutProfile() {
        given(userRepository.findByIdForWrite(10L)).willReturn(Optional.of(UserEntity.builder()
                .id(10L).enabled(false).pendingDeletion(true).build()));
        SellerPayoutProfileDTO.UpsertRequest request = SellerPayoutProfileDTO.UpsertRequest.builder()
                .userId(10L).provider("TOSS").providerSellerId("seller_10").build();

        assertThatThrownBy(() -> sellerPayoutProfileService.upsert(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PAYOUT_PROFILE_ACCOUNT_UNAVAILABLE");
        verifyNoInteractions(sellerPayoutProfileRepository);
    }

    @Test
    void upsert_rejectsProviderSellerIdLongerThan120AfterTrimmingBeforeProfileAccess() {
        given(userRepository.findByIdForWrite(10L)).willReturn(Optional.of(UserEntity.builder()
                .id(10L).enabled(true).pendingDeletion(false).build()));
        SellerPayoutProfileDTO.UpsertRequest request = SellerPayoutProfileDTO.UpsertRequest.builder()
                .userId(10L).provider("TOSS").providerSellerId("s".repeat(121)).build();

        assertThatThrownBy(() -> sellerPayoutProfileService.upsert(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("providerSellerId는 공백 제거 후 120자 이하여야 합니다.");

        verifyNoInteractions(sellerPayoutProfileRepository);
    }

    @Test
    void upsert_rejectsKycStatusLongerThan50BeforeProfileAccess() {
        given(userRepository.findByIdForWrite(10L)).willReturn(Optional.of(UserEntity.builder()
                .id(10L).enabled(true).pendingDeletion(false).build()));
        SellerPayoutProfileDTO.UpsertRequest request = SellerPayoutProfileDTO.UpsertRequest.builder()
                .userId(10L).provider("TOSS").providerSellerId("seller_10").kycStatus("k".repeat(51)).build();

        assertThatThrownBy(() -> sellerPayoutProfileService.upsert(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("kycStatus는 50자 이하여야 합니다.");

        verifyNoInteractions(sellerPayoutProfileRepository);
    }

    @Test
    void upsert_accepts120CharacterTrimmedSellerIdAnd50CharacterKycStatus() {
        String normalizedSellerId = "s".repeat(120);
        String kycStatus = "k".repeat(50);
        given(userRepository.findByIdForWrite(10L)).willReturn(Optional.of(UserEntity.builder()
                .id(10L).enabled(true).pendingDeletion(false).build()));
        given(sellerPayoutProfileRepository.findByProviderAndProviderSellerId("TOSS", normalizedSellerId))
                .willReturn(Optional.empty());
        given(sellerPayoutProfileRepository.findByUserIdAndProviderForUpdate(10L, "TOSS"))
                .willReturn(Optional.empty());
        given(sellerPayoutProfileRepository.save(any(SellerPayoutProfile.class)))
                .willAnswer(inv -> inv.getArgument(0));
        SellerPayoutProfileDTO.UpsertRequest request = SellerPayoutProfileDTO.UpsertRequest.builder()
                .userId(10L)
                .provider("TOSS")
                .providerSellerId("  " + normalizedSellerId + " \t")
                .kycStatus(kycStatus)
                .build();

        SellerPayoutProfile profile = sellerPayoutProfileService.upsert(request);

        assertThat(profile.getProviderSellerId()).isEqualTo(normalizedSellerId);
        assertThat(profile.getKycStatus()).isEqualTo(kycStatus);
    }
}
