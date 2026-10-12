package com.example.mate.service;

import com.example.auth.service.AccountDeletionFinancialService;
import com.example.mate.entity.PayoutTransaction;
import com.example.mate.entity.PaymentTransaction;
import com.example.mate.entity.PaymentStatus;
import com.example.mate.entity.PartyApplication;
import com.example.mate.entity.Party;
import com.example.auth.entity.UserEntity;
import com.example.mate.entity.SettlementStatus;
import com.example.mate.repository.PayoutTransactionRepository;
import com.example.mate.repository.PaymentTransactionRepository;
import com.example.mate.repository.PartyApplicationRepository;
import com.example.mate.repository.PartyRepository;
import com.example.auth.repository.UserRepository;
import com.example.mate.service.payout.PayoutGateway;
import com.example.common.jobs.JobSubmissionGateway;
import org.jobrunr.jobs.lambdas.JobLambda;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatusCode;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.spy;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
class PayoutServiceTest {

    @Mock
    private PayoutTransactionRepository payoutTransactionRepository;

    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;

    @Mock
    private AccountDeletionFinancialService accountDeletionFinancialService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PartyRepository partyRepository;

    @Mock
    private PartyApplicationRepository partyApplicationRepository;

    @Mock
    private MateLinkReconciliationCaseRecorder reconciliationCaseRecorder;

    @Mock
    private PaymentMetricsService paymentMetricsService;

    @Mock
    private SellerPayoutProfileService sellerPayoutProfileService;

    @Mock
    private SellerRecoveryService sellerRecoveryService;

    @Mock
    private PayoutStateService payoutStateService;

    @Mock
    private JobSubmissionGateway jobScheduler;

    @Mock
    private PayoutGateway simGateway;

    @Mock
    private PayoutGateway tossGateway;

    private PayoutService newServiceWithEnabled(boolean payoutEnabled) throws Exception {
        return newServiceWithConfig(payoutEnabled, "SIM", List.of(simGateway));
    }

    private PayoutService newServiceWithConfig(boolean payoutEnabled, String provider, List<PayoutGateway> gateways)
            throws Exception {
        return newServiceWithConfig(payoutEnabled, provider, gateways, true);
    }

    private PayoutService newServiceWithConfig(
            boolean payoutEnabled,
            String provider,
            List<PayoutGateway> gateways,
            boolean dispatchIntentAllowed) throws Exception {
        lenient().when(jobScheduler.isAvailable()).thenReturn(true);
        Map<Long, PayoutTransaction> claimedPayouts = new HashMap<>();
        lenient().when(payoutTransactionRepository.saveAndFlush(any(PayoutTransaction.class)))
                .thenAnswer(invocation -> {
                    PayoutTransaction payout = invocation.getArgument(0);
                    claimedPayouts.put(payout.getId(), payout);
                    return payout;
                });
        lenient().when(payoutTransactionRepository.findByIdForUpdate(any()))
                .thenAnswer(invocation -> {
                    Long payoutId = invocation.getArgument(0);
                    PayoutTransaction claimed = claimedPayouts.get(payoutId);
                    return claimed != null
                            ? Optional.of(claimed)
                            : payoutTransactionRepository.findById(payoutId);
                });
        if (gateways.contains(simGateway)) {
            when(simGateway.getProviderCode()).thenReturn("SIM");
        }
        if (gateways.contains(tossGateway)) {
            when(tossGateway.getProviderCode()).thenReturn("TOSS");
        }

        PayoutClaimService claimService = spy(new PayoutClaimService(
                payoutTransactionRepository,
                paymentTransactionRepository,
                userRepository,
                partyRepository,
                partyApplicationRepository,
                reconciliationCaseRecorder,
                sellerRecoveryService,
                paymentMetricsService));
        lenient().doReturn(true).when(claimService)
                .claimProviderDispatchIntent(any(Long.class), any(Long.class));
        if (!dispatchIntentAllowed) {
            lenient().doReturn(false).when(claimService)
                    .claimProviderDispatchIntent(any(Long.class), any(Long.class));
        }

        PayoutService payoutService = new PayoutService(
                payoutTransactionRepository,
                paymentTransactionRepository,
                accountDeletionFinancialService,
                sellerPayoutProfileService,
                claimService,
                new PayoutStateService(
                        payoutTransactionRepository,
                        paymentTransactionRepository,
                        sellerRecoveryService,
                        paymentMetricsService),
                jobScheduler,
                gateways);

        Field providerField = PayoutService.class.getDeclaredField("payoutProvider");
        providerField.setAccessible(true);
        providerField.set(payoutService, provider);

        Field enabledField = PayoutService.class.getDeclaredField("payoutEnabled");
        enabledField.setAccessible(true);
        enabledField.setBoolean(payoutService, payoutEnabled);

        return payoutService;
    }

    @Test
    void requestPayout_fallbackCleanupFailureKeepsDurableClaimRequestedWithoutDispatch() {
        PaymentTransaction payment = PaymentTransaction.builder()
                .id(201L)
                .sellerUserId(202L)
                .netAmount(5000)
                .paymentStatus(PaymentStatus.PAID)
                .settlementStatus(SettlementStatus.REQUESTED)
                .build();
        PayoutTransaction payout = PayoutTransaction.builder()
                .id(203L)
                .paymentTransactionId(201L)
                .sellerId(202L)
                .requestedAmount(5000)
                .status(SettlementStatus.REQUESTED)
                .retryCount(0)
                .nextRetryAt(Instant.now().plusSeconds(30))
                .build();
        PayoutClaimService claimService = org.mockito.Mockito.mock(PayoutClaimService.class);
        when(claimService.claimInitial(201L, true)).thenReturn(new PayoutClaimService.ClaimedPayout(
                payment, payout, PayoutClaimService.ClaimAction.CALL_PROVIDER));
        when(claimService.bindProviderPayload(201L, 203L, "SIM", null)).thenAnswer(invocation -> {
            payout.setProviderCode("SIM");
            return payout;
        });
        when(payoutStateService.keepRequested(
                eq(payment), eq(payout), eq(null), eq("PAYOUT_FALLBACK_CLEANUP_FAILED"), any(), eq(false)))
                .thenReturn(payout);
        org.mockito.Mockito.doThrow(new IllegalStateException("synthetic cleanup failure"))
                .when(accountDeletionFinancialService).clearFallbackIfNoUnboundPayouts(202L);
        when(simGateway.getProviderCode()).thenReturn("SIM");

        PayoutService payoutService = new PayoutService(
                payoutTransactionRepository,
                paymentTransactionRepository,
                accountDeletionFinancialService,
                sellerPayoutProfileService,
                claimService,
                payoutStateService,
                jobScheduler,
                List.of(simGateway));
        when(jobScheduler.isAvailable()).thenReturn(true);
        org.springframework.test.util.ReflectionTestUtils.setField(payoutService, "payoutEnabled", true);
        org.springframework.test.util.ReflectionTestUtils.setField(payoutService, "payoutProvider", "SIM");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> payoutService.requestPayout(payment))
                .isInstanceOf(RuntimeException.class)
                .hasCauseInstanceOf(IllegalStateException.class);

        assertThat(payout.getStatus()).isEqualTo(SettlementStatus.REQUESTED);
        assertThat(payout.getProviderCode()).isEqualTo("SIM");
        verify(payoutStateService).keepRequested(
                eq(payment),
                eq(payout),
                eq(null),
                eq("PAYOUT_FALLBACK_CLEANUP_FAILED"),
                org.mockito.ArgumentMatchers.contains("Seller payout fallback cleanup failed"),
                eq(false));
        org.mockito.InOrder snapshotThenCleanup = inOrder(claimService, accountDeletionFinancialService);
        snapshotThenCleanup.verify(claimService).bindProviderPayload(201L, 203L, "SIM", null);
        snapshotThenCleanup.verify(accountDeletionFinancialService).clearFallbackIfNoUnboundPayouts(202L);
        verify(payoutStateService, never()).fail(
                any(), any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyBoolean());
        verify(claimService, never()).claimProviderDispatchIntent(any(), any());
        verify(simGateway, never()).requestPayout(any());
        verify(jobScheduler).schedule(any(Instant.class), any(JobLambda.class));
    }

    @Test
    void retryPayout_fallbackCleanupFailureSkipsProviderStatusLookup() {
        PaymentTransaction payment = PaymentTransaction.builder()
                .id(204L)
                .sellerUserId(205L)
                .paymentStatus(PaymentStatus.PAID)
                .settlementStatus(SettlementStatus.REQUESTED)
                .build();
        PayoutTransaction payout = PayoutTransaction.builder()
                .id(206L)
                .paymentTransactionId(204L)
                .sellerId(205L)
                .requestedAmount(5000)
                .status(SettlementStatus.REQUESTED)
                .providerRef("synthetic-provider-ref")
                .providerCode("TOSS")
                .providerSellerId("synthetic-seller-id")
                .retryCount(0)
                .nextRetryAt(Instant.now().plusSeconds(30))
                .build();
        PayoutClaimService claimService = org.mockito.Mockito.mock(PayoutClaimService.class);
        when(claimService.claimRetry(206L, true)).thenReturn(new PayoutClaimService.ClaimedPayout(
                payment, payout, PayoutClaimService.ClaimAction.POLL_PROVIDER));
        when(claimService.bindProviderPayload(204L, 206L, "TOSS", "synthetic-seller-id"))
                .thenReturn(payout);
        when(payoutStateService.keepRequested(
                eq(payment),
                eq(payout),
                eq("synthetic-provider-ref"),
                eq("PAYOUT_FALLBACK_CLEANUP_FAILED"),
                any(),
                eq(false))).thenReturn(payout);
        org.mockito.Mockito.doThrow(new IllegalStateException("synthetic cleanup failure"))
                .when(accountDeletionFinancialService).clearFallbackIfNoUnboundPayouts(205L);
        when(tossGateway.getProviderCode()).thenReturn("TOSS");

        PayoutService payoutService = new PayoutService(
                payoutTransactionRepository,
                paymentTransactionRepository,
                accountDeletionFinancialService,
                sellerPayoutProfileService,
                claimService,
                payoutStateService,
                jobScheduler,
                List.of(tossGateway));
        when(jobScheduler.isAvailable()).thenReturn(true);
        org.springframework.test.util.ReflectionTestUtils.setField(payoutService, "payoutEnabled", true);
        org.springframework.test.util.ReflectionTestUtils.setField(payoutService, "payoutProvider", "TOSS");

        payoutService.retryPayout(206L);

        verify(claimService).bindProviderPayload(204L, 206L, "TOSS", "synthetic-seller-id");
        verify(accountDeletionFinancialService).clearFallbackIfNoUnboundPayouts(205L);
        verify(tossGateway, never()).getPayoutStatus(any());
        verify(tossGateway, never()).requestPayout(any());
        verify(payoutStateService).keepRequested(
                eq(payment),
                eq(payout),
                eq("synthetic-provider-ref"),
                eq("PAYOUT_FALLBACK_CLEANUP_FAILED"),
                org.mockito.ArgumentMatchers.contains("Seller payout fallback cleanup failed"),
                eq(false));
    }

    @Test
    void disabledFeatureDoesNotScanOrResumeSkippedPayouts() throws Exception {
        PayoutService payoutService = newServiceWithEnabled(false);

        payoutService.resumePayoutClaimsSkippedWhileDisabled();

        verify(payoutTransactionRepository, never())
                .findTop100ByStatusAndFailureCodeOrderByIdAsc(any(), any());
        verify(jobScheduler, never()).requireWritable();
        verify(simGateway, never()).requestPayout(any(PayoutGateway.PayoutRequest.class));
    }

    @Test
    void enabledFeatureScansOnlyPayoutsSkippedByDisabledFeature() throws Exception {
        given(payoutTransactionRepository.findTop100ByStatusAndFailureCodeOrderByIdAsc(
                SettlementStatus.SKIPPED,
                PayoutClaimService.PAYOUT_DISABLED_FAILURE_CODE)).willReturn(List.of());
        PayoutService payoutService = newServiceWithEnabled(true);

        payoutService.resumePayoutClaimsSkippedWhileDisabled();

        verify(payoutTransactionRepository).findTop100ByStatusAndFailureCodeOrderByIdAsc(
                SettlementStatus.SKIPPED,
                PayoutClaimService.PAYOUT_DISABLED_FAILURE_CODE);
        verify(jobScheduler).requireWritable();
        verify(simGateway, never()).requestPayout(any(PayoutGateway.PayoutRequest.class));
    }

    @Test
    void enabledFeatureResumesEligibleDisabledPayoutAndDoesNotRedispatchCompletedCandidate() throws Exception {
        PaymentTransaction payment = PaymentTransaction.builder()
                .id(120L)
                .sellerUserId(130L)
                .partyId(140L)
                .applicationId(150L)
                .buyerUserId(160L)
                .orderId("synthetic-disabled-payout-order")
                .paymentKey("synthetic-disabled-payout-key")
                .grossAmount(12000)
                .netAmount(12000)
                .paymentStatus(PaymentStatus.PAID)
                .settlementStatus(SettlementStatus.SKIPPED)
                .build();
        Party party = Party.builder()
                .id(140L)
                .hostId(130L)
                .status(Party.PartyStatus.SELLING)
                .gameDate(LocalDate.now().plusDays(1))
                .build();
        PartyApplication application = PartyApplication.builder()
                .id(150L)
                .partyId(140L)
                .applicantId(160L)
                .orderId("synthetic-disabled-payout-order")
                .paymentKey("synthetic-disabled-payout-key")
                .depositAmount(12000)
                .isApproved(true)
                .isPaid(true)
                .isRejected(false)
                .build();
        PayoutTransaction payout = PayoutTransaction.builder()
                .id(170L)
                .paymentTransactionId(120L)
                .sellerId(130L)
                .requestedAmount(12000)
                .status(SettlementStatus.SKIPPED)
                .failureCode(PayoutClaimService.PAYOUT_DISABLED_FAILURE_CODE)
                .retryCount(0)
                .recoveryOffsetAmount(0)
                .build();

        given(payoutTransactionRepository.findTop100ByStatusAndFailureCodeOrderByIdAsc(
                SettlementStatus.SKIPPED,
                PayoutClaimService.PAYOUT_DISABLED_FAILURE_CODE)).willReturn(List.of(payout));
        given(payoutTransactionRepository.findById(170L)).willReturn(Optional.of(payout));
        given(paymentTransactionRepository.findById(120L)).willReturn(Optional.of(payment));
        given(paymentTransactionRepository.findByIdForUpdate(120L)).willReturn(Optional.of(payment));
        given(userRepository.findByIdForWrite(130L)).willReturn(Optional.of(UserEntity.builder()
                .id(130L)
                .enabled(true)
                .pendingDeletion(false)
                .build()));
        given(partyRepository.findByIdForUpdate(140L)).willReturn(Optional.of(party));
        given(partyApplicationRepository.findByOrderIdForUpdate("synthetic-disabled-payout-order"))
                .willReturn(Optional.of(application));
        given(simGateway.requestPayout(any(PayoutGateway.PayoutRequest.class)))
                .willReturn(new PayoutGateway.PayoutResult("synthetic-disabled-resume-ref", "COMPLETED"));

        PayoutService payoutService = newServiceWithEnabled(true);
        payoutService.resumePayoutClaimsSkippedWhileDisabled();
        payoutService.resumePayoutClaimsSkippedWhileDisabled();

        assertThat(payout.getStatus()).isEqualTo(SettlementStatus.COMPLETED);
        assertThat(payment.getSettlementStatus()).isEqualTo(SettlementStatus.COMPLETED);
        verify(simGateway).requestPayout(argThat(request ->
                Integer.valueOf(12000).equals(request.amount())
                        && "mate-payout-120".equals(request.requestId())));
    }

    @Test
    void requestPayout_returnsExistingCompletedWithoutRequest() throws Exception {
        PaymentTransaction paymentTransaction = PaymentTransaction.builder()
                .id(10L)
                .sellerUserId(20L)
                .netAmount(12000)
                .build();

        PayoutTransaction existing = PayoutTransaction.builder()
                .id(33L)
                .paymentTransactionId(10L)
                .sellerId(20L)
                .requestedAmount(12000)
                .status(SettlementStatus.COMPLETED)
                .retryCount(0)
                .build();

        givenLockedPayment(paymentTransaction);
        given(payoutTransactionRepository.findTopByPaymentTransactionIdForUpdateOrderByIdDesc(10L))
                .willReturn(Optional.of(existing));

        PayoutService payoutService = newServiceWithEnabled(true);
        PayoutTransaction result = payoutService.requestPayout(paymentTransaction);

        assertThat(result).isSameAs(existing);
        verify(simGateway, never()).requestPayout(any(PayoutGateway.PayoutRequest.class));
    }

    @Test
    void requestPayout_disabledEnvironmentMarksSkippedAndUpdatesSettlement() throws Exception {
        PaymentTransaction paymentTransaction = PaymentTransaction.builder()
                .id(10L)
                .sellerUserId(20L)
                .netAmount(12000)
                .build();

        PayoutTransaction pending = PayoutTransaction.builder()
                .id(33L)
                .paymentTransactionId(10L)
                .sellerId(20L)
                .requestedAmount(12000)
                .status(SettlementStatus.PENDING)
                .retryCount(0)
                .build();

        givenLockedPayment(paymentTransaction);
        given(payoutTransactionRepository.findTopByPaymentTransactionIdForUpdateOrderByIdDesc(10L))
                .willReturn(Optional.of(pending));
        given(payoutTransactionRepository.save(any(PayoutTransaction.class)))
                .willAnswer(inv -> inv.getArgument(0));

        PayoutService payoutService = newServiceWithEnabled(false);
        PayoutTransaction result = payoutService.requestPayout(paymentTransaction);

        assertThat(result.getStatus()).isEqualTo(SettlementStatus.SKIPPED);
        assertThat(paymentTransaction.getSettlementStatus()).isEqualTo(SettlementStatus.SKIPPED);
        verify(simGateway, never()).requestPayout(any(PayoutGateway.PayoutRequest.class));
    }

    @Test
    void requestPayout_unknownExecutionFailureRemainsRequestedAndSchedulesReconciliation() throws Exception {
        PaymentTransaction paymentTransaction = PaymentTransaction.builder()
                .id(10L)
                .sellerUserId(20L)
                .netAmount(12000)
                .build();

        PayoutTransaction failed = PayoutTransaction.builder()
                .id(33L)
                .paymentTransactionId(10L)
                .sellerId(20L)
                .requestedAmount(12000)
                .status(SettlementStatus.PENDING)
                .retryCount(0)
                .build();

        givenLockedPayment(paymentTransaction);
        given(simGateway.requestPayout(any(PayoutGateway.PayoutRequest.class)))
                .willThrow(new RuntimeException("provider unavailable"));
        given(payoutTransactionRepository.findTopByPaymentTransactionIdForUpdateOrderByIdDesc(10L))
                .willReturn(Optional.of(failed));
        given(payoutTransactionRepository.save(any(PayoutTransaction.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(paymentTransactionRepository.save(any(PaymentTransaction.class)))
                .willAnswer(inv -> inv.getArgument(0));

        PayoutService payoutService = newServiceWithEnabled(true);

        assertThatThrownBy(() -> payoutService.requestPayout(paymentTransaction))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("provider unavailable");

        assertThat(failed.getStatus()).isEqualTo(SettlementStatus.REQUESTED);
        assertThat(failed.getRetryCount()).isEqualTo(1);
        assertThat(failed.getNextRetryAt()).isNotNull();
        assertThat(failed.getNextRetryAt()).isAfter(Instant.now().minusSeconds(1));
        assertThat(paymentTransaction.getSettlementStatus()).isEqualTo(SettlementStatus.REQUESTED);

        verify(jobScheduler).schedule(any(Instant.class), any(JobLambda.class));
    }

    @Test
    void requestPayout_deniedDispatchIntentDoesNotCallProvider() throws Exception {
        PaymentTransaction paymentTransaction = PaymentTransaction.builder()
                .id(109L)
                .sellerUserId(220L)
                .netAmount(12000)
                .paymentStatus(PaymentStatus.PAID)
                .build();
        PayoutTransaction pending = PayoutTransaction.builder()
                .id(309L)
                .paymentTransactionId(109L)
                .sellerId(220L)
                .requestedAmount(12000)
                .status(SettlementStatus.PENDING)
                .retryCount(0)
                .build();
        givenLockedPayment(paymentTransaction);
        given(payoutTransactionRepository.findTopByPaymentTransactionIdForUpdateOrderByIdDesc(109L))
                .willReturn(Optional.of(pending));
        given(payoutTransactionRepository.findById(309L)).willReturn(Optional.of(pending));

        PayoutService payoutService = newServiceWithConfig(true, "SIM", List.of(simGateway), false);
        PayoutTransaction result = payoutService.requestPayout(paymentTransaction);

        assertThat(result).isSameAs(pending);
        verify(simGateway, never()).requestPayout(any(PayoutGateway.PayoutRequest.class));
    }

    @Test
    void requestPayout_recordsSellerProfileMissingWithoutRetryWhenProviderIsToss() throws Exception {
        PaymentTransaction paymentTransaction = PaymentTransaction.builder()
                .id(44L)
                .orderId("MATE-44-2-1700000000000")
                .sellerUserId(77L)
                .netAmount(25000)
                .build();

        PayoutTransaction pending = PayoutTransaction.builder()
                .id(55L)
                .paymentTransactionId(44L)
                .sellerId(77L)
                .requestedAmount(25000)
                .status(SettlementStatus.PENDING)
                .retryCount(0)
                .build();

        givenLockedPayment(paymentTransaction);
        given(payoutTransactionRepository.findTopByPaymentTransactionIdForUpdateOrderByIdDesc(44L))
                .willReturn(Optional.of(pending));
        given(payoutTransactionRepository.save(any(PayoutTransaction.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(paymentTransactionRepository.save(any(PaymentTransaction.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(sellerPayoutProfileService.getRequiredProviderSellerId(77L, "TOSS"))
                .willThrow(new IllegalStateException("SELLER_PROFILE_MISSING"));

        PayoutService payoutService = newServiceWithConfig(true, "TOSS", List.of(simGateway, tossGateway));

        assertThatThrownBy(() -> payoutService.requestPayout(paymentTransaction))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SELLER_PROFILE_MISSING");

        assertThat(pending.getStatus()).isEqualTo(SettlementStatus.FAILED);
        assertThat(pending.getFailureCode()).isEqualTo("SELLER_PROFILE_MISSING");
        verify(jobScheduler, never()).schedule(any(Instant.class), any(JobLambda.class));
    }

    @Test
    void requestPayout_tossProviderAcceptedRemainsRequestedUntilProviderCompletes() throws Exception {
        PaymentTransaction paymentTransaction = PaymentTransaction.builder()
                .id(66L)
                .orderId("MATE-66-2-1700000000000")
                .sellerUserId(88L)
                .netAmount(33000)
                .build();

        PayoutTransaction pending = PayoutTransaction.builder()
                .id(67L)
                .paymentTransactionId(66L)
                .sellerId(88L)
                .requestedAmount(33000)
                .status(SettlementStatus.PENDING)
                .retryCount(0)
                .build();

        givenLockedPayment(paymentTransaction);
        given(payoutTransactionRepository.findTopByPaymentTransactionIdForUpdateOrderByIdDesc(66L))
                .willReturn(Optional.of(pending));
        given(payoutTransactionRepository.save(any(PayoutTransaction.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(paymentTransactionRepository.save(any(PaymentTransaction.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(sellerPayoutProfileService.getRequiredProviderSellerId(88L, "TOSS"))
                .willReturn("seller-toss-88");
        given(tossGateway.requestPayout(any(PayoutGateway.PayoutRequest.class)))
                .willReturn(new PayoutGateway.PayoutResult("payout-ref-001", "REQUESTED"));

        PayoutService payoutService = newServiceWithConfig(true, "TOSS", List.of(simGateway, tossGateway));
        PayoutTransaction result = payoutService.requestPayout(paymentTransaction);

        assertThat(result.getStatus()).isEqualTo(SettlementStatus.REQUESTED);
        assertThat(result.getProviderRef()).isEqualTo("payout-ref-001");
        assertThat(paymentTransaction.getSettlementStatus()).isEqualTo(SettlementStatus.REQUESTED);
        verify(tossGateway).requestPayout(argThat(request ->
                "seller-toss-88".equals(request.providerSellerId())
                        && "MATE-66-2-1700000000000".equals(request.orderId())
                        && Integer.valueOf(33000).equals(request.amount())));
        verify(jobScheduler).schedule(any(Instant.class), any(JobLambda.class));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "\t"})
    void requestPayout_completedWithoutProviderRefRequiresManualReconciliation(String providerRef) throws Exception {
        PaymentTransaction paymentTransaction = PaymentTransaction.builder()
                .id(118L)
                .orderId("MATE-118-2-1700000000000")
                .sellerUserId(128L)
                .netAmount(33000)
                .build();
        PayoutTransaction pending = PayoutTransaction.builder()
                .id(119L)
                .paymentTransactionId(118L)
                .sellerId(128L)
                .requestedAmount(33000)
                .status(SettlementStatus.PENDING)
                .retryCount(0)
                .build();

        givenLockedPayment(paymentTransaction);
        given(payoutTransactionRepository.findTopByPaymentTransactionIdForUpdateOrderByIdDesc(118L))
                .willReturn(Optional.of(pending));
        given(payoutTransactionRepository.save(any(PayoutTransaction.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(paymentTransactionRepository.save(any(PaymentTransaction.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(sellerPayoutProfileService.getRequiredProviderSellerId(128L, "TOSS"))
                .willReturn("seller-toss-128");
        given(tossGateway.requestPayout(any(PayoutGateway.PayoutRequest.class)))
                .willReturn(new PayoutGateway.PayoutResult(providerRef, "COMPLETED"));

        PayoutTransaction result = newServiceWithConfig(true, "TOSS", List.of(simGateway, tossGateway))
                .requestPayout(paymentTransaction);

        assertThat(result.getStatus()).isEqualTo(SettlementStatus.REQUESTED);
        assertThat(result.getProviderRef()).isNull();
        assertThat(result.getFailureCode()).isEqualTo("PAYOUT_MANUAL_RECONCILIATION_REQUIRED");
        assertThat(result.getNextRetryAt()).isNull();
        assertThat(result.getRetryCount()).isZero();
        assertThat(paymentTransaction.getSettlementStatus()).isEqualTo(SettlementStatus.REQUESTED);
        verify(jobScheduler, never()).schedule(any(Instant.class), any(JobLambda.class));
        verify(sellerRecoveryService, never()).applyReservedOffset(any(PayoutTransaction.class));
    }

    @Test
    void requestPayout_tossProviderFailureStoresFailureCodeAndSchedulesRetry() throws Exception {
        PaymentTransaction paymentTransaction = PaymentTransaction.builder()
                .id(77L)
                .orderId("MATE-77-2-1700000000000")
                .sellerUserId(99L)
                .netAmount(41000)
                .build();

        PayoutTransaction pending = PayoutTransaction.builder()
                .id(78L)
                .paymentTransactionId(77L)
                .sellerId(99L)
                .requestedAmount(41000)
                .status(SettlementStatus.PENDING)
                .retryCount(0)
                .build();

        givenLockedPayment(paymentTransaction);
        given(payoutTransactionRepository.findTopByPaymentTransactionIdForUpdateOrderByIdDesc(77L))
                .willReturn(Optional.of(pending));
        given(payoutTransactionRepository.save(any(PayoutTransaction.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(paymentTransactionRepository.save(any(PaymentTransaction.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(sellerPayoutProfileService.getRequiredProviderSellerId(99L, "TOSS"))
                .willReturn("seller-toss-99");
        given(tossGateway.requestPayout(any(PayoutGateway.PayoutRequest.class)))
                .willThrow(new PayoutGateway.PayoutGatewayException(
                        "invalid seller profile",
                        "TOSS_SELLER_INVALID",
                        HttpStatusCode.valueOf(400)));

        PayoutService payoutService = newServiceWithConfig(true, "TOSS", List.of(simGateway, tossGateway));

        assertThatThrownBy(() -> payoutService.requestPayout(paymentTransaction))
                .isInstanceOf(PayoutGateway.PayoutGatewayException.class)
                .hasMessageContaining("invalid seller profile");

        assertThat(pending.getStatus()).isEqualTo(SettlementStatus.FAILED);
        assertThat(pending.getFailureCode()).isEqualTo("TOSS_SELLER_INVALID");
        assertThat(paymentTransaction.getSettlementStatus()).isEqualTo(SettlementStatus.FAILED);
        verify(jobScheduler, never()).schedule(any(Instant.class), any(JobLambda.class));
    }

    @Test
    void requestPayout_timeoutHttpFailureRemainsRequestedUntilReconciled() throws Exception {
        PaymentTransaction paymentTransaction = PaymentTransaction.builder()
                .id(79L)
                .orderId("MATE-79-2-1700000000000")
                .sellerUserId(100L)
                .netAmount(42000)
                .build();
        PayoutTransaction pending = PayoutTransaction.builder()
                .id(80L)
                .paymentTransactionId(79L)
                .sellerId(100L)
                .requestedAmount(42000)
                .status(SettlementStatus.PENDING)
                .retryCount(0)
                .build();

        givenLockedPayment(paymentTransaction);
        given(payoutTransactionRepository.findTopByPaymentTransactionIdForUpdateOrderByIdDesc(79L))
                .willReturn(Optional.of(pending));
        given(payoutTransactionRepository.save(any(PayoutTransaction.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(paymentTransactionRepository.save(any(PaymentTransaction.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(sellerPayoutProfileService.getRequiredProviderSellerId(100L, "TOSS"))
                .willReturn("seller-toss-100");
        given(tossGateway.requestPayout(any(PayoutGateway.PayoutRequest.class)))
                .willThrow(new PayoutGateway.PayoutGatewayException(
                        "response lost",
                        "TOSS_TIMEOUT",
                        HttpStatusCode.valueOf(408)));

        PayoutService payoutService = newServiceWithConfig(true, "TOSS", List.of(simGateway, tossGateway));

        assertThatThrownBy(() -> payoutService.requestPayout(paymentTransaction))
                .isInstanceOf(PayoutGateway.PayoutGatewayException.class);

        assertThat(pending.getStatus()).isEqualTo(SettlementStatus.REQUESTED);
        assertThat(paymentTransaction.getSettlementStatus()).isEqualTo(SettlementStatus.REQUESTED);
        assertThat(pending.getFailureCode()).isEqualTo("TOSS_TIMEOUT");
        assertThat(pending.getNextRetryAt()).isNotNull();
        verify(jobScheduler).schedule(any(Instant.class), any(JobLambda.class));
    }

    @Test
    void requestPayout_timeoutCannotBecomeDefinitiveFromSellerInvalidBodyCode() throws Exception {
        PaymentTransaction paymentTransaction = PaymentTransaction.builder()
                .id(81L)
                .orderId("MATE-81-2-1700000000000")
                .sellerUserId(101L)
                .netAmount(43000)
                .build();
        PayoutTransaction pending = PayoutTransaction.builder()
                .id(82L)
                .paymentTransactionId(81L)
                .sellerId(101L)
                .requestedAmount(43000)
                .status(SettlementStatus.PENDING)
                .retryCount(0)
                .build();

        givenLockedPayment(paymentTransaction);
        given(payoutTransactionRepository.findTopByPaymentTransactionIdForUpdateOrderByIdDesc(81L))
                .willReturn(Optional.of(pending));
        given(payoutTransactionRepository.save(any(PayoutTransaction.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(paymentTransactionRepository.save(any(PaymentTransaction.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(sellerPayoutProfileService.getRequiredProviderSellerId(101L, "TOSS"))
                .willReturn("seller-toss-101");
        given(tossGateway.requestPayout(any(PayoutGateway.PayoutRequest.class)))
                .willThrow(new PayoutGateway.PayoutGatewayException(
                        "response lost",
                        "TOSS_SELLER_INVALID",
                        HttpStatusCode.valueOf(408)));

        PayoutService payoutService = newServiceWithConfig(true, "TOSS", List.of(simGateway, tossGateway));

        assertThatThrownBy(() -> payoutService.requestPayout(paymentTransaction))
                .isInstanceOf(PayoutGateway.PayoutGatewayException.class);

        assertThat(pending.getStatus()).isEqualTo(SettlementStatus.REQUESTED);
        assertThat(paymentTransaction.getSettlementStatus()).isEqualTo(SettlementStatus.REQUESTED);
    }

    @Test
    void requestPayout_fullRecoveryOffsetCompletesWithoutProviderCall() throws Exception {
        PaymentTransaction paymentTransaction = PaymentTransaction.builder()
                .id(90L)
                .orderId("MATE-90-2-1700000000000")
                .sellerUserId(120L)
                .netAmount(12000)
                .build();
        PayoutTransaction pending = PayoutTransaction.builder()
                .id(91L)
                .paymentTransactionId(90L)
                .sellerId(120L)
                .requestedAmount(12000)
                .status(SettlementStatus.PENDING)
                .retryCount(0)
                .build();

        givenLockedPayment(paymentTransaction);
        given(payoutTransactionRepository.findTopByPaymentTransactionIdForUpdateOrderByIdDesc(90L))
                .willReturn(Optional.of(pending));
        given(payoutTransactionRepository.save(any(PayoutTransaction.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(paymentTransactionRepository.save(any(PaymentTransaction.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(sellerRecoveryService.reserveOffset(pending, 120L, 12000))
                .willReturn(new SellerRecoveryService.RecoveryOffsetResult(12000));

        PayoutTransaction result = newServiceWithEnabled(true).requestPayout(paymentTransaction);

        assertThat(result.getStatus()).isEqualTo(SettlementStatus.COMPLETED);
        assertThat(result.getRequestedAmount()).isZero();
        assertThat(result.getRecoveryOffsetAmount()).isEqualTo(12000);
        assertThat(result.getRecoveryOffsetReservedAt()).isNotNull();
        assertThat(result.getProviderRef()).isEqualTo("RECOVERY_OFFSET");
        assertThat(result.getClaimProtocol()).isEqualTo("SNAPSHOT_V1");
        assertThat(result.getFailureCode()).isEqualTo("PAYOUT_COMPLETION_VERIFIED");
        assertThat(paymentTransaction.getSettlementStatus()).isEqualTo(SettlementStatus.COMPLETED);
        verify(simGateway, never()).requestPayout(any());
    }

    @Test
    void requestPayout_partialRecoveryOffsetSendsOnlyRemainder() throws Exception {
        PaymentTransaction paymentTransaction = PaymentTransaction.builder()
                .id(92L)
                .orderId("MATE-92-2-1700000000000")
                .sellerUserId(121L)
                .netAmount(12000)
                .build();
        PayoutTransaction pending = PayoutTransaction.builder()
                .id(93L)
                .paymentTransactionId(92L)
                .sellerId(121L)
                .requestedAmount(12000)
                .status(SettlementStatus.PENDING)
                .retryCount(0)
                .build();

        givenLockedPayment(paymentTransaction);
        given(payoutTransactionRepository.findTopByPaymentTransactionIdForUpdateOrderByIdDesc(92L))
                .willReturn(Optional.of(pending));
        given(payoutTransactionRepository.save(any(PayoutTransaction.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(paymentTransactionRepository.save(any(PaymentTransaction.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(sellerRecoveryService.reserveOffset(pending, 121L, 12000))
                .willReturn(new SellerRecoveryService.RecoveryOffsetResult(3000));
        given(simGateway.requestPayout(any(PayoutGateway.PayoutRequest.class)))
                .willReturn(new PayoutGateway.PayoutResult("sim-partial-offset", "COMPLETED"));

        PayoutTransaction result = newServiceWithEnabled(true).requestPayout(paymentTransaction);

        assertThat(result.getRecoveryOffsetAmount()).isEqualTo(3000);
        assertThat(result.getRequestedAmount()).isEqualTo(9000);
        verify(simGateway).requestPayout(argThat(request -> Integer.valueOf(9000).equals(request.amount())));
    }

    @Test
    void retryPayout_terminalFailureNeverReissuesProviderRequest() throws Exception {
        PaymentTransaction paymentTransaction = PaymentTransaction.builder()
                .id(94L)
                .orderId("MATE-94-2-1700000000000")
                .sellerUserId(122L)
                .netAmount(12000)
                .paymentStatus(PaymentStatus.PAID)
                .build();
        PayoutTransaction failed = PayoutTransaction.builder()
                .id(95L)
                .paymentTransactionId(94L)
                .sellerId(122L)
                .requestedAmount(9000)
                .recoveryOffsetAmount(3000)
                .recoveryOffsetReservedAt(Instant.now().minusSeconds(60))
                .status(SettlementStatus.FAILED)
                .retryCount(1)
                .nextRetryAt(Instant.now().minusSeconds(1))
                .build();

        given(payoutTransactionRepository.findById(95L)).willReturn(Optional.of(failed));
        given(paymentTransactionRepository.findByIdForUpdate(94L)).willReturn(Optional.of(paymentTransaction));
        newServiceWithEnabled(true).retryPayout(95L);

        assertThat(failed.getStatus()).isEqualTo(SettlementStatus.FAILED);
        verify(sellerRecoveryService, never()).reserveOffset(any(), any(), any(Integer.class));
        verify(simGateway, never()).requestPayout(any());
    }

    @Test
    void retryPayout_canceledPaymentSkipsWithoutProviderCall() throws Exception {
        PaymentTransaction paymentTransaction = PaymentTransaction.builder()
                .id(96L)
                .orderId("MATE-96-2-1700000000000")
                .sellerUserId(123L)
                .netAmount(12000)
                .paymentStatus(PaymentStatus.CANCELED)
                .settlementStatus(SettlementStatus.SKIPPED)
                .build();
        PayoutTransaction failed = PayoutTransaction.builder()
                .id(97L)
                .paymentTransactionId(96L)
                .sellerId(123L)
                .requestedAmount(12000)
                .status(SettlementStatus.FAILED)
                .retryCount(1)
                .nextRetryAt(Instant.now().minusSeconds(1))
                .build();

        given(payoutTransactionRepository.findById(97L)).willReturn(Optional.of(failed));
        given(paymentTransactionRepository.findByIdForUpdate(96L)).willReturn(Optional.of(paymentTransaction));
        newServiceWithEnabled(true).retryPayout(97L);

        assertThat(failed.getStatus()).isEqualTo(SettlementStatus.FAILED);
        assertThat(paymentTransaction.getSettlementStatus()).isEqualTo(SettlementStatus.SKIPPED);
        verify(simGateway, never()).requestPayout(any());
        verify(sellerRecoveryService, never()).reserveOffset(any(), any(), any(Integer.class));
    }

    @Test
    void retryPayout_missingPartyHoldsBeforeCallingProvider() throws Exception {
        PaymentTransaction paymentTransaction = PaymentTransaction.builder()
                .id(98L)
                .sellerUserId(124L)
                .partyId(null)
                .netAmount(12000)
                .paymentStatus(PaymentStatus.PAID)
                .settlementStatus(SettlementStatus.REQUESTED)
                .build();
        PayoutTransaction requested = PayoutTransaction.builder()
                .id(99L)
                .paymentTransactionId(98L)
                .sellerId(124L)
                .status(SettlementStatus.REQUESTED)
                .claimProtocol("SNAPSHOT_V1")
                .retryCount(0)
                .nextRetryAt(Instant.now().minusSeconds(1))
                .build();

        given(payoutTransactionRepository.findById(99L)).willReturn(Optional.of(requested));
        given(paymentTransactionRepository.findByIdForUpdate(98L)).willReturn(Optional.of(paymentTransaction));

        newServiceWithEnabled(true).retryPayout(99L);

        assertThat(requested.getStatus()).isEqualTo(SettlementStatus.REQUESTED);
        assertThat(requested.getFailureCode())
                .isEqualTo(PayoutStateService.MANUAL_RECONCILIATION_FAILURE_CODE);
        assertThat(requested.getNextRetryAt()).isNull();
        assertThat(paymentTransaction.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(paymentTransaction.getSettlementStatus()).isEqualTo(SettlementStatus.REQUESTED);
        verify(sellerRecoveryService, never()).reserveOffset(any(), any(), any(Integer.class));
        verify(simGateway, never()).requestPayout(any());
        verify(tossGateway, never()).requestPayout(any());
        verify(simGateway, never()).getPayoutStatus(any());
        verify(tossGateway, never()).getPayoutStatus(any());
    }

    @Test
    void retryPayout_whenDispatchIntentClaimAllowsItUsesStableProviderRequestId() throws Exception {
        PaymentTransaction paymentTransaction = PaymentTransaction.builder()
                .id(99L)
                .orderId("MATE-99-2-1700000000000")
                .sellerUserId(125L)
                .partyId(149L)
                .netAmount(12000)
                .paymentStatus(PaymentStatus.PAID)
                .settlementStatus(SettlementStatus.REQUESTED)
                .build();
        PayoutTransaction requested = PayoutTransaction.builder()
                .id(100L)
                .paymentTransactionId(99L)
                .sellerId(125L)
                .requestedAmount(12000)
                .recoveryOffsetAmount(0)
                .recoveryOffsetReservedAt(Instant.now().minusSeconds(60))
                .status(SettlementStatus.REQUESTED)
                .claimProtocol("SNAPSHOT_V1")
                .failureCode("TOSS_PAYOUT_REQUEST_FAILED")
                .retryCount(1)
                .nextRetryAt(Instant.now().minusSeconds(1))
                .build();

        given(payoutTransactionRepository.findById(100L)).willReturn(Optional.of(requested));
        given(paymentTransactionRepository.findByIdForUpdate(99L)).willReturn(Optional.of(paymentTransaction));
        given(payoutTransactionRepository.save(any(PayoutTransaction.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(paymentTransactionRepository.save(any(PaymentTransaction.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(partyRepository.findById(149L)).willReturn(Optional.of(Party.builder()
                .id(149L)
                .status(Party.PartyStatus.SELLING)
                .build()));
        given(sellerPayoutProfileService.getRequiredProviderSellerId(125L, "TOSS"))
                .willReturn("seller-toss-125");
        given(tossGateway.requestPayout(any(PayoutGateway.PayoutRequest.class)))
                .willReturn(new PayoutGateway.PayoutResult("provider-100", "COMPLETED"));

        newServiceWithConfig(true, "TOSS", List.of(simGateway, tossGateway)).retryPayout(100L);

        verify(tossGateway).requestPayout(argThat(request ->
                "mate-payout-99".equals(request.requestId())));
        assertThat(requested.getStatus()).isEqualTo(SettlementStatus.COMPLETED);
        assertThat(paymentTransaction.getSettlementStatus()).isEqualTo(SettlementStatus.COMPLETED);
    }

    @Test
    void retryPayout_requestedWithProviderRefPollsStatusInsteadOfPayingAgain() throws Exception {
        PaymentTransaction paymentTransaction = PaymentTransaction.builder()
                .id(101L)
                .orderId("MATE-101-2-1700000000000")
                .sellerUserId(126L)
                .netAmount(12000)
                .paymentStatus(PaymentStatus.PAID)
                .settlementStatus(SettlementStatus.REQUESTED)
                .build();
        PayoutTransaction requested = PayoutTransaction.builder()
                .id(102L)
                .paymentTransactionId(101L)
                .sellerId(126L)
                .requestedAmount(12000)
                .status(SettlementStatus.REQUESTED)
                .providerRef("provider-102")
                .providerCode("TOSS")
                .providerSellerId("seller-toss-126")
                .claimProtocol("SNAPSHOT_V1")
                .retryCount(0)
                .nextRetryAt(Instant.now().minusSeconds(1))
                .build();

        given(payoutTransactionRepository.findById(102L)).willReturn(Optional.of(requested));
        given(paymentTransactionRepository.findByIdForUpdate(101L)).willReturn(Optional.of(paymentTransaction));
        given(payoutTransactionRepository.save(any(PayoutTransaction.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(paymentTransactionRepository.save(any(PaymentTransaction.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(tossGateway.getPayoutStatus("provider-102"))
                .willReturn(new PayoutGateway.PayoutStatusResult(
                        "provider-102", "COMPLETED", null, null));

        newServiceWithConfig(true, "TOSS", List.of(simGateway, tossGateway)).retryPayout(102L);

        verify(tossGateway).getPayoutStatus("provider-102");
        verify(tossGateway, never()).requestPayout(any());
        assertThat(requested.getStatus()).isEqualTo(SettlementStatus.COMPLETED);
        assertThat(paymentTransaction.getSettlementStatus()).isEqualTo(SettlementStatus.COMPLETED);
    }

    @Test
    void retryPayout_mismatchedStatusProviderRefRequiresManualReconciliation() throws Exception {
        PaymentTransaction paymentTransaction = PaymentTransaction.builder()
                .id(113L)
                .paymentStatus(PaymentStatus.PAID)
                .settlementStatus(SettlementStatus.REQUESTED)
                .build();
        PayoutTransaction requested = PayoutTransaction.builder()
                .id(114L)
                .paymentTransactionId(113L)
                .status(SettlementStatus.REQUESTED)
                .providerRef("provider-original")
                .providerCode("TOSS")
                .providerSellerId("seller-toss-113")
                .claimProtocol("SNAPSHOT_V1")
                .retryCount(0)
                .nextRetryAt(Instant.now().minusSeconds(1))
                .build();

        given(payoutTransactionRepository.findById(114L)).willReturn(Optional.of(requested));
        given(paymentTransactionRepository.findByIdForUpdate(113L)).willReturn(Optional.of(paymentTransaction));
        given(payoutTransactionRepository.save(any(PayoutTransaction.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(paymentTransactionRepository.save(any(PaymentTransaction.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(tossGateway.getPayoutStatus("provider-original"))
                .willReturn(new PayoutGateway.PayoutStatusResult("provider-other", "COMPLETED", null, null));

        newServiceWithConfig(true, "TOSS", List.of(simGateway, tossGateway)).retryPayout(114L);

        assertThat(requested.getStatus()).isEqualTo(SettlementStatus.REQUESTED);
        assertThat(requested.getProviderRef()).isEqualTo("provider-original");
        assertThat(requested.getFailureCode()).isEqualTo("PAYOUT_MANUAL_RECONCILIATION_REQUIRED");
        assertThat(requested.getNextRetryAt()).isNull();
        assertThat(paymentTransaction.getSettlementStatus()).isEqualTo(SettlementStatus.REQUESTED);
        verify(tossGateway).getPayoutStatus("provider-original");
        verify(jobScheduler, never()).schedule(any(Instant.class), any(JobLambda.class));
    }

    @Test
    void retryPayout_missingStatusProviderRefRequiresManualReconciliation() throws Exception {
        PaymentTransaction paymentTransaction = PaymentTransaction.builder()
                .id(115L)
                .paymentStatus(PaymentStatus.PAID)
                .settlementStatus(SettlementStatus.REQUESTED)
                .build();
        PayoutTransaction requested = PayoutTransaction.builder()
                .id(116L)
                .paymentTransactionId(115L)
                .status(SettlementStatus.REQUESTED)
                .providerRef("provider-116")
                .providerCode("TOSS")
                .providerSellerId("seller-toss-115")
                .claimProtocol("SNAPSHOT_V1")
                .retryCount(0)
                .nextRetryAt(Instant.now().minusSeconds(1))
                .build();

        given(payoutTransactionRepository.findById(116L)).willReturn(Optional.of(requested));
        given(paymentTransactionRepository.findByIdForUpdate(115L)).willReturn(Optional.of(paymentTransaction));
        given(payoutTransactionRepository.save(any(PayoutTransaction.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(paymentTransactionRepository.save(any(PaymentTransaction.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(tossGateway.getPayoutStatus("provider-116"))
                .willReturn(new PayoutGateway.PayoutStatusResult(null, "COMPLETED", null, null));

        newServiceWithConfig(true, "TOSS", List.of(simGateway, tossGateway)).retryPayout(116L);

        assertThat(requested.getStatus()).isEqualTo(SettlementStatus.REQUESTED);
        assertThat(requested.getProviderRef()).isEqualTo("provider-116");
        assertThat(requested.getFailureCode()).isEqualTo("PAYOUT_MANUAL_RECONCILIATION_REQUIRED");
        assertThat(requested.getNextRetryAt()).isNull();
        assertThat(paymentTransaction.getSettlementStatus()).isEqualTo(SettlementStatus.REQUESTED);
        verify(jobScheduler, never()).schedule(any(Instant.class), any(JobLambda.class));
    }

    @Test
    void requestPayout_unknownProviderStatusRemainsRequested() throws Exception {
        PaymentTransaction paymentTransaction = PaymentTransaction.builder()
                .id(103L)
                .orderId("MATE-103-2-1700000000000")
                .sellerUserId(127L)
                .netAmount(13000)
                .build();
        PayoutTransaction pending = PayoutTransaction.builder()
                .id(104L)
                .paymentTransactionId(103L)
                .sellerId(127L)
                .requestedAmount(13000)
                .status(SettlementStatus.PENDING)
                .retryCount(0)
                .build();

        givenLockedPayment(paymentTransaction);
        given(payoutTransactionRepository.findTopByPaymentTransactionIdForUpdateOrderByIdDesc(103L))
                .willReturn(Optional.of(pending));
        given(payoutTransactionRepository.save(any(PayoutTransaction.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(paymentTransactionRepository.save(any(PaymentTransaction.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(sellerPayoutProfileService.getRequiredProviderSellerId(127L, "TOSS"))
                .willReturn("seller-toss-127");
        given(tossGateway.requestPayout(any(PayoutGateway.PayoutRequest.class)))
                .willReturn(new PayoutGateway.PayoutResult("provider-104", "UNKNOWN"));

        newServiceWithConfig(true, "TOSS", List.of(simGateway, tossGateway))
                .requestPayout(paymentTransaction);

        assertThat(pending.getStatus()).isEqualTo(SettlementStatus.REQUESTED);
        assertThat(paymentTransaction.getSettlementStatus()).isEqualTo(SettlementStatus.REQUESTED);
        verify(jobScheduler).schedule(any(Instant.class), any(JobLambda.class));
    }

    @Test
    void requestPayout_terminalProviderFailureDoesNotScheduleRetry() throws Exception {
        PaymentTransaction paymentTransaction = PaymentTransaction.builder()
                .id(105L)
                .orderId("MATE-105-2-1700000000000")
                .sellerUserId(128L)
                .netAmount(14000)
                .build();
        PayoutTransaction pending = PayoutTransaction.builder()
                .id(106L)
                .paymentTransactionId(105L)
                .sellerId(128L)
                .requestedAmount(14000)
                .status(SettlementStatus.PENDING)
                .retryCount(0)
                .build();

        givenLockedPayment(paymentTransaction);
        given(payoutTransactionRepository.findTopByPaymentTransactionIdForUpdateOrderByIdDesc(105L))
                .willReturn(Optional.of(pending));
        given(payoutTransactionRepository.save(any(PayoutTransaction.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(paymentTransactionRepository.save(any(PaymentTransaction.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(sellerPayoutProfileService.getRequiredProviderSellerId(128L, "TOSS"))
                .willReturn("seller-toss-128");
        given(tossGateway.requestPayout(any(PayoutGateway.PayoutRequest.class)))
                .willReturn(new PayoutGateway.PayoutResult("provider-106", "FAILED"));

        newServiceWithConfig(true, "TOSS", List.of(simGateway, tossGateway))
                .requestPayout(paymentTransaction);

        assertThat(pending.getStatus()).isEqualTo(SettlementStatus.FAILED);
        assertThat(pending.getRetryCount()).isEqualTo(1);
        assertThat(pending.getNextRetryAt()).isNull();
        assertThat(paymentTransaction.getSettlementStatus()).isEqualTo(SettlementStatus.FAILED);
        verify(jobScheduler, never()).schedule(any(Instant.class), any(JobLambda.class));
    }

    @Test
    void retryPayout_providerStillRequestedKeepsRefundBlockedAndReschedules() throws Exception {
        PaymentTransaction paymentTransaction = PaymentTransaction.builder()
                .id(107L)
                .paymentStatus(PaymentStatus.PAID)
                .settlementStatus(SettlementStatus.REQUESTED)
                .build();
        PayoutTransaction requested = PayoutTransaction.builder()
                .id(108L)
                .paymentTransactionId(107L)
                .status(SettlementStatus.REQUESTED)
                .providerRef("provider-108")
                .providerCode("TOSS")
                .providerSellerId("seller-toss-107")
                .claimProtocol("SNAPSHOT_V1")
                .retryCount(0)
                .nextRetryAt(Instant.now().minusSeconds(1))
                .build();

        given(payoutTransactionRepository.findById(108L)).willReturn(Optional.of(requested));
        given(paymentTransactionRepository.findByIdForUpdate(107L)).willReturn(Optional.of(paymentTransaction));
        given(payoutTransactionRepository.save(any(PayoutTransaction.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(paymentTransactionRepository.save(any(PaymentTransaction.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(tossGateway.getPayoutStatus("provider-108"))
                .willReturn(new PayoutGateway.PayoutStatusResult(
                        "provider-108", "REQUESTED", null, null));

        newServiceWithConfig(true, "TOSS", List.of(simGateway, tossGateway)).retryPayout(108L);

        assertThat(requested.getStatus()).isEqualTo(SettlementStatus.REQUESTED);
        assertThat(requested.getRetryCount()).isEqualTo(1);
        assertThat(paymentTransaction.getSettlementStatus()).isEqualTo(SettlementStatus.REQUESTED);
        verify(jobScheduler).schedule(any(Instant.class), any(JobLambda.class));
    }

    @Test
    void retryPayout_terminalProviderFailureBecomesFailed() throws Exception {
        PaymentTransaction paymentTransaction = PaymentTransaction.builder()
                .id(109L)
                .paymentStatus(PaymentStatus.PAID)
                .settlementStatus(SettlementStatus.REQUESTED)
                .build();
        PayoutTransaction requested = PayoutTransaction.builder()
                .id(110L)
                .paymentTransactionId(109L)
                .status(SettlementStatus.REQUESTED)
                .providerRef("provider-110")
                .providerCode("TOSS")
                .providerSellerId("seller-toss-109")
                .claimProtocol("SNAPSHOT_V1")
                .retryCount(0)
                .nextRetryAt(Instant.now().minusSeconds(1))
                .build();

        given(payoutTransactionRepository.findById(110L)).willReturn(Optional.of(requested));
        given(paymentTransactionRepository.findByIdForUpdate(109L)).willReturn(Optional.of(paymentTransaction));
        given(payoutTransactionRepository.save(any(PayoutTransaction.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(paymentTransactionRepository.save(any(PaymentTransaction.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(tossGateway.getPayoutStatus("provider-110"))
                .willReturn(new PayoutGateway.PayoutStatusResult(
                        "provider-110", "FAILED", "TOSS_PAYOUT_FAILED", "provider rejected payout"));

        newServiceWithConfig(true, "TOSS", List.of(simGateway, tossGateway)).retryPayout(110L);

        assertThat(requested.getStatus()).isEqualTo(SettlementStatus.FAILED);
        assertThat(requested.getRetryCount()).isEqualTo(1);
        assertThat(requested.getNextRetryAt()).isNull();
        assertThat(paymentTransaction.getSettlementStatus()).isEqualTo(SettlementStatus.FAILED);
        verify(jobScheduler, never()).schedule(any(Instant.class), any(JobLambda.class));
    }

    @Test
    void retryPayout_exhaustedRequestedStateRequiresManualReconciliation() throws Exception {
        PaymentTransaction paymentTransaction = PaymentTransaction.builder()
                .id(111L)
                .paymentStatus(PaymentStatus.PAID)
                .settlementStatus(SettlementStatus.REQUESTED)
                .build();
        PayoutTransaction requested = PayoutTransaction.builder()
                .id(112L)
                .paymentTransactionId(111L)
                .status(SettlementStatus.REQUESTED)
                .providerRef("provider-112")
                .claimProtocol("SNAPSHOT_V1")
                .retryCount(5)
                .nextRetryAt(Instant.now().minusSeconds(1))
                .build();

        given(payoutTransactionRepository.findById(112L)).willReturn(Optional.of(requested));
        given(paymentTransactionRepository.findByIdForUpdate(111L)).willReturn(Optional.of(paymentTransaction));
        given(payoutTransactionRepository.save(any(PayoutTransaction.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(paymentTransactionRepository.save(any(PaymentTransaction.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        newServiceWithConfig(true, "TOSS", List.of(simGateway, tossGateway)).retryPayout(112L);

        assertThat(requested.getStatus()).isEqualTo(SettlementStatus.REQUESTED);
        assertThat(requested.getFailureCode()).isEqualTo("PAYOUT_MANUAL_RECONCILIATION_REQUIRED");
        assertThat(requested.getNextRetryAt()).isNull();
        assertThat(paymentTransaction.getSettlementStatus()).isEqualTo(SettlementStatus.REQUESTED);
        verify(tossGateway, never()).getPayoutStatus(any());
        verify(jobScheduler, never()).schedule(any(Instant.class), any(JobLambda.class));
    }

    @Test
    void requestPayout_flushesUniqueClaimBeforeCallingProvider() throws Exception {
        PaymentTransaction paymentTransaction = PaymentTransaction.builder()
                .id(98L)
                .orderId("MATE-98-2-1700000000000")
                .sellerUserId(124L)
                .netAmount(12000)
                .paymentStatus(PaymentStatus.PAID)
                .build();
        PayoutTransaction pending = PayoutTransaction.builder()
                .id(199L)
                .paymentTransactionId(98L)
                .sellerId(124L)
                .requestedAmount(12000)
                .status(SettlementStatus.PENDING)
                .retryCount(0)
                .build();

        given(payoutTransactionRepository.findTopByPaymentTransactionIdForUpdateOrderByIdDesc(98L))
                .willReturn(Optional.of(pending));
        givenLockedPayment(paymentTransaction);
        given(payoutTransactionRepository.save(any(PayoutTransaction.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(paymentTransactionRepository.save(any(PaymentTransaction.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(simGateway.requestPayout(any(PayoutGateway.PayoutRequest.class)))
                .willReturn(new PayoutGateway.PayoutResult("sim-unique-claim", "COMPLETED"));

        newServiceWithEnabled(true).requestPayout(paymentTransaction);

        org.mockito.InOrder order = inOrder(payoutTransactionRepository, simGateway);
        order.verify(payoutTransactionRepository).saveAndFlush(pending);
        order.verify(simGateway).requestPayout(argThat(request ->
                "mate-payout-98".equals(request.requestId())));
    }

    private void givenLockedPayment(PaymentTransaction paymentTransaction) {
        paymentTransaction.setPaymentStatus(PaymentStatus.PAID);
        Long paymentId = paymentTransaction.getId();
        Long sellerId = paymentTransaction.getSellerUserId();
        Long partyId = paymentTransaction.getPartyId() != null
                ? paymentTransaction.getPartyId()
                : 500_000L + paymentId;
        Long applicationId = paymentTransaction.getApplicationId() != null
                ? paymentTransaction.getApplicationId()
                : 600_000L + paymentId;
        Long buyerId = paymentTransaction.getBuyerUserId() != null
                ? paymentTransaction.getBuyerUserId()
                : 700_000L + paymentId;
        String orderId = paymentTransaction.getOrderId() != null && !paymentTransaction.getOrderId().isBlank()
                ? paymentTransaction.getOrderId()
                : "synthetic-payout-order-" + paymentId;
        String paymentKey = paymentTransaction.getPaymentKey() != null && !paymentTransaction.getPaymentKey().isBlank()
                ? paymentTransaction.getPaymentKey()
                : "synthetic-payout-payment-" + paymentId;
        Integer grossAmount = paymentTransaction.getGrossAmount() != null
                ? paymentTransaction.getGrossAmount()
                : paymentTransaction.getNetAmount() != null ? paymentTransaction.getNetAmount() : 1;

        paymentTransaction.setPartyId(partyId);
        paymentTransaction.setApplicationId(applicationId);
        paymentTransaction.setBuyerUserId(buyerId);
        paymentTransaction.setOrderId(orderId);
        paymentTransaction.setPaymentKey(paymentKey);
        paymentTransaction.setGrossAmount(grossAmount);

        Party party = Party.builder()
                .id(partyId)
                .hostId(sellerId)
                .status(Party.PartyStatus.SELLING)
                .gameDate(LocalDate.now().plusDays(1))
                .build();
        PartyApplication application = PartyApplication.builder()
                .id(applicationId)
                .partyId(partyId)
                .applicantId(buyerId)
                .orderId(orderId)
                .paymentKey(paymentKey)
                .depositAmount(grossAmount)
                .isApproved(true)
                .isPaid(true)
                .isRejected(false)
                .build();

        given(paymentTransactionRepository.findById(paymentId))
                .willReturn(Optional.of(paymentTransaction));
        given(paymentTransactionRepository.findByIdForUpdate(paymentTransaction.getId()))
                .willReturn(Optional.of(paymentTransaction));
        given(userRepository.findByIdForWrite(sellerId))
                .willReturn(Optional.of(UserEntity.builder().id(sellerId).build()));
        given(partyRepository.findByIdForUpdate(partyId)).willReturn(Optional.of(party));
        given(partyApplicationRepository.findByOrderIdForUpdate(orderId))
                .willReturn(Optional.of(application));
    }
}
