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

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
    private JobScheduler jobScheduler;

    @Mock
    private PayoutGateway simGateway;

    private PayoutService newServiceWithEnabled(boolean payoutEnabled) throws Exception {
        when(simGateway.getProviderCode()).thenReturn("SIM");

        PayoutService payoutService = new PayoutService(
                payoutTransactionRepository,
                paymentTransactionRepository,
                paymentMetricsService,
                jobScheduler,
                List.of(simGateway));

        Field providerField = PayoutService.class.getDeclaredField("payoutProvider");
        providerField.setAccessible(true);
        providerField.set(payoutService, "SIM");

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
        verify(simGateway, never()).requestPayout(any(PaymentTransaction.class));
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
        verify(simGateway, never()).requestPayout(any(PaymentTransaction.class));
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

        given(simGateway.requestPayout(any(PaymentTransaction.class)))
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
}
