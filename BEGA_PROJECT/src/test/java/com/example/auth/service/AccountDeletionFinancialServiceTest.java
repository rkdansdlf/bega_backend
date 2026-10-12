package com.example.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.example.mate.entity.PayoutTransaction;
import com.example.mate.entity.PartyApplication;
import com.example.mate.entity.SellerPayoutProfile;
import com.example.mate.entity.SettlementStatus;
import com.example.mate.repository.PayoutTransactionRepository;
import com.example.mate.repository.PaymentTransactionRepository;
import com.example.mate.repository.PartyApplicationRepository;
import com.example.mate.repository.SellerPayoutProfileRepository;
import com.example.auth.repository.UserRepository;
import com.example.auth.entity.UserEntity;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountDeletionFinancialServiceTest {

    @Mock
    private SellerPayoutProfileRepository sellerPayoutProfileRepository;

    @Mock
    private PayoutTransactionRepository payoutTransactionRepository;

    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PartyApplicationRepository partyApplicationRepository;

    @InjectMocks
    private AccountDeletionFinancialService service;

    @Test
    void retainsOnlySellerIdNeededToRecoverAnUnboundPayoutAndScrubsKycMetadata() {
        SellerPayoutProfile profile = profile();
        PartyApplication application = application();
        when(sellerPayoutProfileRepository.findAllByUserIdForUpdate(7L)).thenReturn(List.of(profile));
        when(payoutTransactionRepository.existsUnboundTossPayoutForSeller(
                7L, List.of(SettlementStatus.PENDING, SettlementStatus.REQUESTED))).thenReturn(true);
        when(partyApplicationRepository.findByApplicantId(7L)).thenReturn(List.of(application));

        service.anonymize(7L);

        assertThat(profile.getProviderSellerId()).isEqualTo("provider-seller-7");
        assertThat(profile.getKycStatus()).isNull();
        assertThat(profile.getMetadataJson()).isNull();
        assertThat(application.getApplicantName()).isEqualTo("탈퇴한 사용자");
        assertThat(application.getApplicantRating()).isNull();
        assertThat(application.getMessage()).isEqualTo("신청 내용이 비식별 처리되었습니다.");
        assertThat(application.getTicketImageUrl()).isNull();
        assertThat(application.getPaymentKey()).isEqualTo("payment-core-7");
        assertThat(application.getOrderId()).isEqualTo("order-core-7");
        assertThat(application.getDepositAmount()).isEqualTo(25000);
        verify(partyApplicationRepository).saveAll(List.of(application));
        verify(sellerPayoutProfileRepository).saveAll(List.of(profile));
    }

    @Test
    void removesSellerIdWhenNoUnboundPayoutCanNeedTheFallback() {
        SellerPayoutProfile profile = profile();
        when(sellerPayoutProfileRepository.findAllByUserIdForUpdate(7L)).thenReturn(List.of(profile));
        when(payoutTransactionRepository.existsUnboundTossPayoutForSeller(
                7L, List.of(SettlementStatus.PENDING, SettlementStatus.REQUESTED))).thenReturn(false);

        service.anonymize(7L);

        assertThat(profile.getProviderSellerId()).isNull();
        assertThat(profile.getKycStatus()).isNull();
        assertThat(profile.getMetadataJson()).isNull();
    }

    @Test
    void retainsFallbackForApprovedPaidTransactionAwaitingClaimCreation() {
        SellerPayoutProfile profile = profile();
        when(sellerPayoutProfileRepository.findAllByUserIdForUpdate(7L)).thenReturn(List.of(profile));
        when(payoutTransactionRepository.existsUnboundTossPayoutForSeller(
                7L, List.of(SettlementStatus.PENDING, SettlementStatus.REQUESTED))).thenReturn(false);
        when(paymentTransactionRepository.findApprovedWithoutPayoutForSeller(
                7L,
                com.example.mate.entity.PaymentStatus.PAID,
                SettlementStatus.PENDING,
                org.springframework.data.domain.PageRequest.of(0, 1)))
                .thenReturn(List.of(70L));

        service.anonymize(7L);

        assertThat(profile.getProviderSellerId()).isEqualTo("provider-seller-7");
        assertThat(profile.getKycStatus()).isNull();
        assertThat(profile.getMetadataJson()).isNull();
    }

    @Test
    void retainsOnlyTossSellerIdWhenAnIncompletePayoutNeedsFallback() {
        SellerPayoutProfile tossProfile = profile();
        SellerPayoutProfile simProfile = SellerPayoutProfile.builder()
                .id(3L).userId(7L).provider("SIM").providerSellerId("synthetic-sim-id")
                .kycStatus("APPROVED").metadataJson("{}").build();
        when(sellerPayoutProfileRepository.findAllByUserIdForUpdate(7L))
                .thenReturn(List.of(tossProfile, simProfile));
        when(payoutTransactionRepository.existsUnboundTossPayoutForSeller(
                7L, List.of(SettlementStatus.PENDING, SettlementStatus.REQUESTED))).thenReturn(true);

        service.anonymize(7L);

        assertThat(tossProfile.getProviderSellerId()).isEqualTo("provider-seller-7");
        assertThat(simProfile.getProviderSellerId()).isNull();
    }

    @Test
    void doesNotClearSellerFallbackWhileAccountCanStillBeRecovered() {
        UserEntity scheduledAccount = UserEntity.builder()
                .id(7L).enabled(false).pendingDeletion(true)
                .deletionScheduledFor(java.time.LocalDateTime.now().plusDays(1)).build();
        when(userRepository.findByIdForWrite(7L)).thenReturn(java.util.Optional.of(scheduledAccount));

        service.clearFallbackIfNoUnboundPayouts(7L);

        verify(userRepository).findByIdForWrite(7L);
        verify(userRepository, never()).findById(7L);
        verify(payoutTransactionRepository, never()).existsUnboundTossPayoutForSeller(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(sellerPayoutProfileRepository, never()).findAllByUserIdForUpdate(7L);
    }

    @Test
    void clearsSellerFallbackAfterFinalizedAccountHasNoUnboundPayouts() {
        SellerPayoutProfile profile = profile();
        UserEntity finalizedAccount = UserEntity.builder()
                .id(7L).enabled(false).pendingDeletion(true).deletionScheduledFor(null).build();
        when(userRepository.findByIdForWrite(7L)).thenReturn(java.util.Optional.of(finalizedAccount));
        when(payoutTransactionRepository.existsUnboundTossPayoutForSeller(
                7L, List.of(SettlementStatus.PENDING, SettlementStatus.REQUESTED))).thenReturn(false);
        when(paymentTransactionRepository.findApprovedWithoutPayoutForSeller(
                7L,
                com.example.mate.entity.PaymentStatus.PAID,
                SettlementStatus.PENDING,
                org.springframework.data.domain.PageRequest.of(0, 1)))
                .thenReturn(List.of());
        when(sellerPayoutProfileRepository.findAllByUserIdForUpdate(7L)).thenReturn(List.of(profile));

        service.clearFallbackIfNoUnboundPayouts(7L);

        assertThat(profile.getProviderSellerId()).isNull();
        org.mockito.InOrder lockBeforeDecision = org.mockito.Mockito.inOrder(
                userRepository, payoutTransactionRepository, paymentTransactionRepository,
                sellerPayoutProfileRepository);
        lockBeforeDecision.verify(userRepository).findByIdForWrite(7L);
        lockBeforeDecision.verify(payoutTransactionRepository).existsUnboundTossPayoutForSeller(
                7L, List.of(SettlementStatus.PENDING, SettlementStatus.REQUESTED));
        verify(sellerPayoutProfileRepository).saveAll(List.of(profile));
    }

    @Test
    void serializesNewPayoutClaimWithAccountFinalization() {
        UserEntity user = UserEntity.builder().id(7L).build();
        when(userRepository.findByIdForWrite(7L)).thenReturn(java.util.Optional.of(user));

        service.lockSellerForPayoutClaim(7L);

        verify(userRepository).findByIdForWrite(7L);
    }

    private SellerPayoutProfile profile() {
        return SellerPayoutProfile.builder()
                .id(2L).userId(7L).provider("TOSS").providerSellerId("provider-seller-7")
                .kycStatus("APPROVED").metadataJson("{\"document\":\"sensitive\"}").build();
    }

    private PartyApplication application() {
        return PartyApplication.builder()
                .id(31L).partyId(12L).applicantId(7L).applicantName("Private Name")
                .applicantRating(4.2).message("private application text").ticketImageUrl("https://user.example/ticket")
                .paymentKey("payment-core-7").orderId("order-core-7").depositAmount(25000).build();
    }
}
