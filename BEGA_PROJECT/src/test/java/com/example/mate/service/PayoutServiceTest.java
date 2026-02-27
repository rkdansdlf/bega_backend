package com.example.mate.service;

import com.example.mate.entity.PayoutTransaction;
import com.example.mate.entity.PaymentTransaction;
import com.example.mate.entity.SettlementStatus;
import com.example.mate.repository.PayoutTransactionRepository;
import com.example.mate.repository.PaymentTransactionRepository;
import com.example.mate.service.payout.PayoutGateway;
import org.jobrunr.scheduling.JobScheduler;
import org.jobrunr.jobs.lambdas.JobLambda;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatusCode;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayoutServiceTest {

    @Mock
    private PayoutTransactionRepository payoutTransactionRepository;

    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;

    @Mock
    private PaymentMetricsService paymentMetricsService;

    @Mock
    private SellerPayoutProfileService sellerPayoutProfileService;

    @Mock
    private JobScheduler jobScheduler;

    @Mock
    private PayoutGateway simGateway;

    @Mock
    private PayoutGateway tossGateway;

    private PayoutService newServiceWithEnabled(boolean payoutEnabled) throws Exception {
        return newServiceWithConfig(payoutEnabled, "SIM", List.of(simGateway));
    }

    private PayoutService newServiceWithConfig(boolean payoutEnabled, String provider) throws Exception {
        return newServiceWithConfig(payoutEnabled, provider, List.of(simGateway));
    }

    private PayoutService newServiceWithConfig(boolean payoutEnabled, String provider, List<PayoutGateway> gateways)
            throws Exception {
        if (gateways.contains(simGateway)) {
            when(simGateway.getProviderCode()).thenReturn("SIM");
        }
        if (gateways.contains(tossGateway)) {
            when(tossGateway.getProviderCode()).thenReturn("TOSS");
        }

        PayoutService payoutService = new PayoutService(
                payoutTransactionRepository,
                paymentTransactionRepository,
                paymentMetricsService,
                sellerPayoutProfileService,
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
    void requestPayout_failedExecutionIncrementsRetryAndSchedules() throws Exception {
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

        assertThat(failed.getStatus()).isEqualTo(SettlementStatus.FAILED);
        assertThat(failed.getRetryCount()).isEqualTo(1);
        assertThat(failed.getNextRetryAt()).isNotNull();
        assertThat(failed.getNextRetryAt()).isAfter(Instant.now().minusSeconds(1));
        assertThat(paymentTransaction.getSettlementStatus()).isEqualTo(SettlementStatus.FAILED);

        verify(jobScheduler).schedule(any(Instant.class), any(JobLambda.class));
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
    void requestPayout_tossProviderSuccessUpdatesCompletedAndProviderRef() throws Exception {
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

        assertThat(result.getStatus()).isEqualTo(SettlementStatus.COMPLETED);
        assertThat(result.getProviderRef()).isEqualTo("payout-ref-001");
        assertThat(paymentTransaction.getSettlementStatus()).isEqualTo(SettlementStatus.COMPLETED);
        verify(tossGateway).requestPayout(argThat(request ->
                "seller-toss-88".equals(request.providerSellerId())
                        && "MATE-66-2-1700000000000".equals(request.orderId())
                        && Integer.valueOf(33000).equals(request.amount())));
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
        verify(jobScheduler).schedule(any(Instant.class), any(JobLambda.class));
    }
}
