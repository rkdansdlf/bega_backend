package com.example.mate.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.auth.service.UserService;
import com.example.common.jobs.JobSubmissionGateway;
import com.example.mate.dto.TossPaymentDTO;
import com.example.mate.entity.Party;
import com.example.mate.entity.PartyApplication;
import com.example.mate.entity.PaymentIntent;
import com.example.mate.entity.PaymentIntentReconciliationEvent;
import com.example.mate.exception.InvalidApplicationStatusException;
import com.example.mate.exception.TossPaymentException;
import com.example.mate.repository.PartyApplicationRepository;
import com.example.mate.repository.PartyRepository;
import com.example.mate.repository.PaymentIntentRepository;
import com.example.mate.repository.PaymentIntentReconciliationEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.jobrunr.jobs.lambdas.JobLambda;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentIntentServiceTest {

        @Mock
        private PaymentIntentRepository paymentIntentRepository;

        @Mock
        private PaymentIntentReconciliationEventRepository reconciliationEventRepository;

        @Mock
        private PartyApplicationRepository applicationRepository;

        @Mock
        private PartyRepository partyRepository;

        @Mock
        private PaymentAmountCalculator paymentAmountCalculator;

        @Mock
        private TossPaymentService tossPaymentService;

        @Mock
        private JobSubmissionGateway jobScheduler;

        @Mock
        private PaymentMetricsService paymentMetricsService;

        @Mock
        private UserService userService;

        @Mock
        private PaymentIntentReconciliationService paymentIntentReconciliationService;

        @InjectMocks
        private PaymentIntentService paymentIntentService;

        @Test
        void resolveIntentForConfirm_throwsWhenAmountChanged() {
                Principal principal = () -> "test@example.com";
                Long applicantId = 10L;

                TossPaymentDTO.ClientConfirmRequest request = TossPaymentDTO.ClientConfirmRequest.builder()
                                .intentId(100L)
                                .orderId("MATE-1-10-1000")
                                .partyId(1L)
                                .paymentKey("pk_test")
                                .flowType(com.example.mate.entity.PaymentFlowType.DEPOSIT)
                                .build();

                PaymentIntent intent = PaymentIntent.builder()
                                .id(100L)
                                .orderId("MATE-1-10-1000")
                                .partyId(1L)
                                .applicantId(applicantId)
                                .expectedAmount(30000)
                                .flowType(com.example.mate.entity.PaymentFlowType.DEPOSIT)
                                .paymentType(PartyApplication.PaymentType.DEPOSIT)
                                .status(PaymentIntent.IntentStatus.PREPARED)
                                .expiresAt(Instant.now().plusSeconds(300))
                                .build();

                Party party = Party.builder()
                                .id(1L)
                                .status(Party.PartyStatus.PENDING)
                                .maxParticipants(6)
                                .currentParticipants(1)
                                .ticketPrice(10000)
                                .build();

                given(userService.getUserIdByEmail("test@example.com")).willReturn(applicantId);
                when(paymentIntentRepository.findByIdAndApplicantIdForUpdate(100L, applicantId))
                                .thenReturn(java.util.Optional.of(intent));
                given(userService.isSocialVerified(applicantId)).willReturn(true);
                given(applicationRepository.findByOrderIdForUpdate("MATE-1-10-1000"))
                                .willReturn(java.util.Optional.empty());
                given(applicationRepository.findByPartyIdAndApplicantId(1L, applicantId))
                                .willReturn(java.util.Optional.empty());
                given(applicationRepository.existsByPartyIdAndApplicantIdAndIsRejectedTrue(1L, applicantId))
                                .willReturn(false);
                given(applicationRepository.countByPartyIdAndIsApprovedFalseAndIsRejectedFalse(1L)).willReturn(0L);
                given(partyRepository.findByIdForUpdate(1L)).willReturn(java.util.Optional.of(party));
                given(paymentAmountCalculator.calculateAmount(1L, com.example.mate.entity.PaymentFlowType.DEPOSIT))
                                .willReturn(new PaymentAmountCalculator.AmountInfo(party, 25000, "KRW", "테스트 주문"));

                assertThrows(InvalidApplicationStatusException.class,
                                () -> paymentIntentService.resolveIntentForConfirm(request, principal));
        }

        @Test
        void resolveIntentForConfirm_rejectsPreparedDepositWhenPartyIsClosed() {
                Long applicantId = 10L;
                TossPaymentDTO.ClientConfirmRequest request = TossPaymentDTO.ClientConfirmRequest.builder()
                                .intentId(100L)
                                .orderId("MATE-1-10-closed")
                                .partyId(1L)
                                .paymentKey("pk_test")
                                .flowType(com.example.mate.entity.PaymentFlowType.DEPOSIT)
                                .build();
                PaymentIntent intent = PaymentIntent.builder()
                                .id(100L)
                                .orderId("MATE-1-10-closed")
                                .partyId(1L)
                                .applicantId(applicantId)
                                .expectedAmount(25000)
                                .flowType(com.example.mate.entity.PaymentFlowType.DEPOSIT)
                                .paymentType(PartyApplication.PaymentType.DEPOSIT)
                                .status(PaymentIntent.IntentStatus.PREPARED)
                                .expiresAt(Instant.now().plusSeconds(300))
                                .build();
                Party closedParty = Party.builder()
                                .id(1L)
                                .status(Party.PartyStatus.FAILED)
                                .maxParticipants(6)
                                .currentParticipants(1)
                                .build();

                given(paymentIntentRepository.findByIdAndApplicantIdForUpdate(100L, applicantId))
                                .willReturn(java.util.Optional.of(intent));
                given(applicationRepository.findByOrderIdForUpdate("MATE-1-10-closed"))
                                .willReturn(java.util.Optional.empty());
                given(userService.isSocialVerified(applicantId)).willReturn(true);
                given(applicationRepository.findByPartyIdAndApplicantId(1L, applicantId))
                                .willReturn(java.util.Optional.empty());
                given(applicationRepository.existsByPartyIdAndApplicantIdAndIsRejectedTrue(1L, applicantId))
                                .willReturn(false);
                given(partyRepository.findById(1L)).willReturn(java.util.Optional.of(closedParty));
                given(partyRepository.findByIdForUpdate(1L)).willReturn(java.util.Optional.of(closedParty));

                assertThrows(InvalidApplicationStatusException.class,
                                () -> paymentIntentService.resolveIntentForConfirm(request, applicantId));

                verify(paymentAmountCalculator, never()).calculateAmount(
                                any(Long.class), any(com.example.mate.entity.PaymentFlowType.class));
        }

        @Test
        void resolveIntentForConfirm_keepsExpiredConfirmedIntentOnCompensationPath() {
                Long applicantId = 10L;
                TossPaymentDTO.ClientConfirmRequest request = TossPaymentDTO.ClientConfirmRequest.builder()
                                .intentId(101L)
                                .orderId("MATE-1-10-confirmed-expired")
                                .partyId(1L)
                                .paymentKey("pk_confirmed")
                                .flowType(com.example.mate.entity.PaymentFlowType.DEPOSIT)
                                .build();
                PaymentIntent intent = PaymentIntent.builder()
                                .id(101L)
                                .orderId("MATE-1-10-confirmed-expired")
                                .partyId(1L)
                                .applicantId(applicantId)
                                .expectedAmount(25000)
                                .paymentKey("pk_confirmed")
                                .flowType(com.example.mate.entity.PaymentFlowType.DEPOSIT)
                                .paymentType(PartyApplication.PaymentType.DEPOSIT)
                                .status(PaymentIntent.IntentStatus.CONFIRMED)
                                .expiresAt(Instant.now().minusSeconds(1))
                                .build();

                given(paymentIntentRepository.findByIdAndApplicantIdForUpdate(101L, applicantId))
                                .willReturn(java.util.Optional.of(intent));
                given(applicationRepository.findByOrderIdForUpdate("MATE-1-10-confirmed-expired"))
                                .willReturn(java.util.Optional.empty());

                PaymentIntent resolved = paymentIntentService.resolveIntentForConfirm(request, applicantId);

                assertThat(resolved).isSameAs(intent);
                assertThat(resolved.getStatus()).isEqualTo(PaymentIntent.IntentStatus.CONFIRMED);
                verify(paymentAmountCalculator, never()).calculateAmount(
                                any(Long.class), any(com.example.mate.entity.PaymentFlowType.class));
                verify(paymentIntentRepository, never()).save(intent);
        }

        @Test
        void compensateAfterApplicationFailure_doesNotCompleteFromAlreadyCanceledMessageWithoutCancellationProof() {
                PaymentIntent intent = PaymentIntent.builder()
                                .id(101L)
                                .orderId("MATE-1-10-2000")
                                .applicantId(10L)
                                .paymentKey("pay_cancel_1")
                                .expectedAmount(25000)
                                .status(PaymentIntent.IntentStatus.CONFIRMED)
                                .build();

                given(paymentIntentRepository.findByIdForUpdate(101L)).willReturn(java.util.Optional.of(intent));
                given(applicationRepository.findByOrderIdForUpdate("MATE-1-10-2000"))
                                .willReturn(java.util.Optional.empty());
                given(tossPaymentService.cancelPayment(eq("pay_cancel_1"), any(), anyInt()))
                                .willThrow(new TossPaymentException("이미 취소된 결제입니다.", HttpStatus.CONFLICT));

                paymentIntentService.compensateAfterApplicationFailure(101L,
                                new IllegalStateException("payment failed"));

                assertThat(intent.getStatus()).isEqualTo(PaymentIntent.IntentStatus.CANCEL_FAILED);
                assertThat(intent.getCanceledAt()).isNull();
                verify(paymentMetricsService).recordCompensationRequested();
                verify(paymentMetricsService).recordCompensation("fail");
                verify(paymentIntentRepository, times(2)).save(intent);
                verify(tossPaymentService).cancelPayment(eq("pay_cancel_1"), any(), anyInt());
                verify(jobScheduler).schedule(any(Instant.class), any(JobLambda.class));
        }

        @Test
        void compensateAfterApplicationFailure_skipsIfApplicationAlreadyExists() {
                PaymentIntent intent = PaymentIntent.builder()
                                .id(108L)
                                .orderId("MATE-1-10-2500")
                                .applicantId(10L)
                                .paymentKey("pay_exist_1")
                                .expectedAmount(25000)
                                .status(PaymentIntent.IntentStatus.CONFIRMED)
                                .build();

                given(paymentIntentRepository.findByIdForUpdate(108L)).willReturn(java.util.Optional.of(intent));
                given(applicationRepository.findByOrderIdForUpdate("MATE-1-10-2500"))
                                .willReturn(java.util.Optional.of(PartyApplication.builder().id(3L).build()));

                paymentIntentService.compensateAfterApplicationFailure(108L,
                                new IllegalStateException("payment failed"));

                assertThat(intent.getStatus()).isEqualTo(PaymentIntent.IntentStatus.APPLICATION_CREATED);
                verify(paymentIntentRepository).save(intent);
                verify(tossPaymentService, never()).cancelPayment(any(), any(), anyInt());
        }

        @Test
        void compensateAfterApplicationFailure_skipsIfAlreadyCancelRequested() {
                PaymentIntent intent = PaymentIntent.builder()
                                .id(109L)
                                .orderId("MATE-1-10-2600")
                                .applicantId(10L)
                                .paymentKey("pay_cancel_requested")
                                .expectedAmount(25000)
                                .status(PaymentIntent.IntentStatus.CANCEL_REQUESTED)
                                .build();

                given(paymentIntentRepository.findByIdForUpdate(109L)).willReturn(java.util.Optional.of(intent));
                given(applicationRepository.findByOrderIdForUpdate("MATE-1-10-2600"))
                                .willReturn(java.util.Optional.empty());

                paymentIntentService.compensateAfterApplicationFailure(109L,
                                new IllegalStateException("payment failed"));

                assertThat(intent.getStatus()).isEqualTo(PaymentIntent.IntentStatus.CANCEL_REQUESTED);
                verify(paymentIntentRepository, never()).save(intent);
                verify(tossPaymentService, never()).cancelPayment(any(), any(), anyInt());
        }

        @Test
        void compensateAfterApplicationFailure_retriesAndRecordsFailureOnCancelFailure() {
                PaymentIntent intent = PaymentIntent.builder()
                                .id(102L)
                                .orderId("MATE-1-10-2001")
                                .applicantId(11L)
                                .paymentKey("pay_cancel_2")
                                .expectedAmount(27000)
                                .status(PaymentIntent.IntentStatus.CONFIRMED)
                                .build();

                given(paymentIntentRepository.findByIdForUpdate(102L)).willReturn(java.util.Optional.of(intent));
                given(applicationRepository.findByOrderIdForUpdate("MATE-1-10-2001"))
                                .willReturn(java.util.Optional.empty());
                given(tossPaymentService.cancelPayment(eq("pay_cancel_2"), any(), anyInt()))
                                .willThrow(new RuntimeException("cancel failed"));

                paymentIntentService.compensateAfterApplicationFailure(102L,
                                new IllegalStateException("payment failed"));

                assertThat(intent.getStatus()).isEqualTo(PaymentIntent.IntentStatus.CANCEL_FAILED);
                assertThat(intent.getFailureCode()).isEqualTo("RuntimeException");
                assertThat(intent.getFailureMessage()).isEqualTo("cancel failed");
                verify(paymentMetricsService).recordCompensation("fail");
                verify(jobScheduler).schedule(any(Instant.class), any(JobLambda.class));
        }

        @Test
        void compensateAfterApplicationFailure_doesNotCompleteWhenCancelResponseIsEmpty() {
                PaymentIntent intent = PaymentIntent.builder()
                                .id(112L)
                                .orderId("MATE-1-10-empty-cancel")
                                .applicantId(11L)
                                .paymentKey("pay_empty_cancel")
                                .expectedAmount(27000)
                                .status(PaymentIntent.IntentStatus.CONFIRMED)
                                .build();

                given(paymentIntentRepository.findByIdForUpdate(112L)).willReturn(java.util.Optional.of(intent));
                given(applicationRepository.findByOrderIdForUpdate("MATE-1-10-empty-cancel"))
                                .willReturn(java.util.Optional.empty());
                given(tossPaymentService.cancelPayment(eq("pay_empty_cancel"), any(), anyInt()))
                                .willReturn(null);

                paymentIntentService.compensateAfterApplicationFailure(112L,
                                new IllegalStateException("payment failed"));

                assertThat(intent.getStatus()).isEqualTo(PaymentIntent.IntentStatus.CANCEL_FAILED);
                assertThat(intent.getCanceledAt()).isNull();
                verify(paymentMetricsService).recordCompensation("fail");
                verify(jobScheduler).schedule(any(Instant.class), any(JobLambda.class));
        }

        @Test
        void retryCompensation_marksApplicationCreatedWhenApplicationAlreadyExists() {
                PaymentIntent intent = PaymentIntent.builder()
                                .id(103L)
                                .orderId("MATE-1-10-3000")
                                .applicantId(12L)
                                .paymentKey("pay_retry_1")
                                .expectedAmount(30000)
                                .status(PaymentIntent.IntentStatus.CANCEL_FAILED)
                                .build();

                given(paymentIntentRepository.findByIdForUpdate(103L)).willReturn(java.util.Optional.of(intent));
                given(applicationRepository.findByOrderIdForUpdate("MATE-1-10-3000"))
                                .willReturn(java.util.Optional.of(PartyApplication.builder().id(3L).build()));

                paymentIntentService.retryCompensation(103L, 1);

                assertThat(intent.getStatus()).isEqualTo(PaymentIntent.IntentStatus.APPLICATION_CREATED);
                verify(paymentIntentRepository).save(intent);
                verify(tossPaymentService, never()).cancelPayment(any(), any(), anyInt());
        }

        @Test
        void retryCompensation_marksFailureAfterMaxAttempts() {
                PaymentIntent intent = PaymentIntent.builder()
                                .id(104L)
                                .orderId("MATE-1-10-3001")
                                .applicantId(13L)
                                .paymentKey("pay_retry_2")
                                .expectedAmount(31000)
                                .status(PaymentIntent.IntentStatus.CANCEL_FAILED)
                                .build();

                given(paymentIntentRepository.findByIdForUpdate(104L)).willReturn(java.util.Optional.of(intent));

                paymentIntentService.retryCompensation(104L, 10);

                assertThat(intent.getStatus()).isEqualTo(PaymentIntent.IntentStatus.CANCEL_FAILED);
                assertThat(intent.getFailureCode()).isEqualTo("MAX_RETRY_REACHED");
                verify(paymentMetricsService).recordCompensation("fail");
        }

        @Test
        void retryCompensation_doesNotCompleteWhenProviderProvesOnlyPartialCancellation() {
                PaymentIntent intent = PaymentIntent.builder()
                                .id(113L)
                                .orderId("MATE-1-10-partial-cancel")
                                .applicantId(13L)
                                .paymentKey("pay_partial_cancel")
                                .expectedAmount(31000)
                                .status(PaymentIntent.IntentStatus.CANCEL_FAILED)
                                .build();
                TossPaymentDTO.CancelResponse response = new TossPaymentDTO.CancelResponse(
                                "pay_partial_cancel", "PARTIAL_CANCELED", 31000);
                response.setBalanceAmount(1000);

                given(paymentIntentRepository.findByIdForUpdate(113L)).willReturn(java.util.Optional.of(intent));
                given(applicationRepository.findByOrderIdForUpdate("MATE-1-10-partial-cancel"))
                                .willReturn(java.util.Optional.empty());
                given(tossPaymentService.cancelPayment(eq("pay_partial_cancel"), any(), anyInt()))
                                .willReturn(response);

                paymentIntentService.retryCompensation(113L, 1);

                assertThat(intent.getStatus()).isEqualTo(PaymentIntent.IntentStatus.CANCEL_FAILED);
                assertThat(intent.getCanceledAt()).isNull();
                verify(paymentMetricsService).recordCompensation("fail");
                verify(jobScheduler).schedule(any(Instant.class), any(JobLambda.class));
        }

        @Test
        void retryCompensation_doesNotCompleteFromAlreadyCanceledErrorWithoutCancellationProof() {
                PaymentIntent intent = PaymentIntent.builder()
                                .id(115L)
                                .orderId("MATE-1-10-already-canceled-retry")
                                .applicantId(13L)
                                .paymentKey("pay_already_canceled_retry")
                                .expectedAmount(31000)
                                .status(PaymentIntent.IntentStatus.CANCEL_FAILED)
                                .build();

                given(paymentIntentRepository.findByIdForUpdate(115L)).willReturn(java.util.Optional.of(intent));
                given(applicationRepository.findByOrderIdForUpdate("MATE-1-10-already-canceled-retry"))
                                .willReturn(java.util.Optional.empty());
                given(tossPaymentService.cancelPayment(eq("pay_already_canceled_retry"), any(), anyInt()))
                                .willThrow(new TossPaymentException(
                                                "결제 취소에 실패했습니다: 409 CONFLICT",
                                                HttpStatus.CONFLICT,
                                                "ALREADY_CANCELED_PAYMENT"));

                paymentIntentService.retryCompensation(115L, 1);

                assertThat(intent.getStatus()).isEqualTo(PaymentIntent.IntentStatus.CANCEL_FAILED);
                assertThat(intent.getCanceledAt()).isNull();
                verify(paymentMetricsService).recordCompensation("fail");
                verify(jobScheduler).schedule(any(Instant.class), any(JobLambda.class));
        }

        @Test
        void cancelPaymentIntent_doesNotCompleteWhenResponsePaymentKeyDoesNotMatch() {
                PaymentIntent intent = PaymentIntent.builder()
                                .id(114L)
                                .orderId("MATE-1-10-mismatched-cancel")
                                .applicantId(10L)
                                .paymentKey("pay_expected_cancel")
                                .expectedAmount(22000)
                                .status(PaymentIntent.IntentStatus.CONFIRMED)
                                .build();
                TossPaymentDTO.CancelResponse response = new TossPaymentDTO.CancelResponse(
                                "pay_other_cancel", "CANCELED", 22000);
                response.setBalanceAmount(0);

                given(paymentIntentRepository.findByIdAndApplicantIdForUpdate(114L, 10L))
                                .willReturn(java.util.Optional.of(intent));
                given(tossPaymentService.cancelPayment(eq("pay_expected_cancel"), any(), anyInt()))
                                .willReturn(response);

                assertThrows(TossPaymentException.class,
                                () -> paymentIntentService.cancelPaymentIntent(114L, 10L, "test cancellation"));

                assertThat(intent.getStatus()).isEqualTo(PaymentIntent.IntentStatus.CANCEL_FAILED);
                assertThat(intent.getCanceledAt()).isNull();
                verify(paymentMetricsService).recordCompensation("user_cancel_fail");
        }

        @Test
        void cancelPaymentIntent_applicationCreatedRejectsWithoutProviderCallOrMutation() {
                PaymentIntent intent = PaymentIntent.builder()
                                .id(117L)
                                .orderId("MATE-1-10-application-created")
                                .applicantId(10L)
                                .paymentKey("pay_application_created")
                                .expectedAmount(22000)
                                .status(PaymentIntent.IntentStatus.APPLICATION_CREATED)
                                .build();

                given(paymentIntentRepository.findByIdAndApplicantIdForUpdate(117L, 10L))
                                .willReturn(java.util.Optional.of(intent));

                TossPaymentException exception = assertThrows(TossPaymentException.class,
                                () -> paymentIntentService.cancelPaymentIntent(117L, 10L, "test cancellation"));

                assertThat(exception).hasMessageContaining("신청 취소 경로");
                assertThat(intent.getStatus()).isEqualTo(PaymentIntent.IntentStatus.APPLICATION_CREATED);
                assertThat(intent.getCanceledAt()).isNull();
                verify(tossPaymentService, never()).cancelPayment(any(), any(), anyInt());
                verify(paymentIntentRepository, never()).save(intent);
        }

        @Test
        void cancelPaymentIntent_rejectsWhenLinkedApplicationExistsEvenIfIntentStatusIsStale() {
                PaymentIntent intent = PaymentIntent.builder()
                                .id(119L)
                                .orderId("MATE-1-10-linked-application")
                                .partyId(1L)
                                .applicantId(10L)
                                .paymentKey("pay_linked_application")
                                .expectedAmount(22000)
                                .status(PaymentIntent.IntentStatus.CONFIRMED)
                                .build();
                PartyApplication application = PartyApplication.builder()
                                .id(29L)
                                .orderId(intent.getOrderId())
                                .partyId(intent.getPartyId())
                                .applicantId(intent.getApplicantId())
                                .paymentKey(intent.getPaymentKey())
                                .depositAmount(intent.getExpectedAmount())
                                .isPaid(true)
                                .build();

                given(paymentIntentRepository.findByIdAndApplicantIdForUpdate(119L, 10L))
                                .willReturn(java.util.Optional.of(intent));
                given(applicationRepository.findByOrderIdForUpdate(intent.getOrderId()))
                                .willReturn(java.util.Optional.of(application));

                TossPaymentException exception = assertThrows(TossPaymentException.class,
                                () -> paymentIntentService.cancelPaymentIntent(119L, 10L, "test cancellation"));

                assertThat(exception.getMessage()).contains("신청 취소 경로");
                assertThat(intent.getStatus()).isEqualTo(PaymentIntent.IntentStatus.CONFIRMED);
                verify(tossPaymentService, never()).cancelPayment(any(), any(), anyInt());
                verify(paymentIntentRepository, never()).save(intent);
        }

        @Test
        void cancelPaymentIntent_rejectsStalePreparedIntentWhenLinkedApplicationExists() {
                PaymentIntent intent = PaymentIntent.builder()
                                .id(123L)
                                .orderId("MATE-1-10-linked-prepared-application")
                                .partyId(1L)
                                .applicantId(10L)
                                .paymentKey("pay_linked_prepared_application")
                                .expectedAmount(22000)
                                .status(PaymentIntent.IntentStatus.PREPARED)
                                .build();
                PartyApplication application = PartyApplication.builder()
                                .id(30L)
                                .orderId(intent.getOrderId())
                                .partyId(intent.getPartyId())
                                .applicantId(intent.getApplicantId())
                                .paymentKey(intent.getPaymentKey())
                                .depositAmount(intent.getExpectedAmount())
                                .isPaid(true)
                                .build();

                given(paymentIntentRepository.findByIdAndApplicantIdForUpdate(123L, 10L))
                                .willReturn(java.util.Optional.of(intent));
                given(applicationRepository.findByOrderIdForUpdate(intent.getOrderId()))
                                .willReturn(java.util.Optional.of(application));

                TossPaymentException exception = assertThrows(TossPaymentException.class,
                                () -> paymentIntentService.cancelPaymentIntent(123L, 10L, "test cancellation"));

                assertThat(exception.getMessage()).contains("신청 취소 경로");
                assertThat(intent.getStatus()).isEqualTo(PaymentIntent.IntentStatus.PREPARED);
                assertThat(intent.getCanceledAt()).isNull();
                verify(tossPaymentService, never()).cancelPayment(any(), any(), anyInt());
                verify(paymentIntentRepository, never()).save(intent);
        }

        @Test
        void reserveConfirmationAttempt_persistsFenceAndPreventsConcurrentCancel() {
                PaymentIntent intent = PaymentIntent.builder()
                                .id(120L)
                                .orderId("MATE-1-10-confirm-reservation")
                                .partyId(1L)
                                .applicantId(10L)
                                .expectedAmount(22000)
                                .status(PaymentIntent.IntentStatus.PREPARED)
                                .build();
                given(paymentIntentRepository.findByIdAndApplicantIdForUpdate(120L, 10L))
                                .willReturn(java.util.Optional.of(intent));

                paymentIntentService.reserveConfirmationAttempt(120L, 10L, "pay_reserved");

                assertThat(intent.getPaymentKey()).isEqualTo("pay_reserved");
                assertThat(intent.getFailureCode()).isEqualTo("CONFIRM_REQUESTED");
                verify(paymentIntentRepository).saveAndFlush(intent);

                TossPaymentException exception = assertThrows(TossPaymentException.class,
                                () -> paymentIntentService.cancelPaymentIntent(120L, 10L, "cancel during confirmation"));

                assertThat(exception.getMessage()).contains("승인 결과를 확인 중");
                assertThat(intent.getStatus()).isEqualTo(PaymentIntent.IntentStatus.PREPARED);
                verify(tossPaymentService, never()).cancelPayment(any(), any(), anyInt());
                verify(paymentIntentRepository, never()).save(intent);
        }

        @Test
        void withApplicantIntentLocked_serializesConfirmAndDirectCancel() throws Exception {
                PaymentIntent intent = PaymentIntent.builder()
                                .id(121L)
                                .orderId("MATE-1-10-confirm-cancel-race")
                                .partyId(1L)
                                .applicantId(10L)
                                .paymentKey("pay_confirm_cancel_race")
                                .expectedAmount(22000)
                                .status(PaymentIntent.IntentStatus.PREPARED)
                                .build();
                ReentrantLock syntheticIntentRowLock = new ReentrantLock();
                AtomicInteger lockAttempts = new AtomicInteger();
                AtomicReference<Thread> cancellationThread = new AtomicReference<>();
                CountDownLatch confirmationInsideLock = new CountDownLatch(1);
                CountDownLatch allowConfirmationToFinish = new CountDownLatch(1);
                CountDownLatch cancellationWaitingForLock = new CountDownLatch(1);
                given(paymentIntentRepository.findByIdAndApplicantIdForUpdate(121L, 10L))
                                .willAnswer(invocation -> {
                                        if (lockAttempts.incrementAndGet() == 2) {
                                                cancellationThread.set(Thread.currentThread());
                                                cancellationWaitingForLock.countDown();
                                        }
                                        syntheticIntentRowLock.lock();
                                        return Optional.of(intent);
                                });

                ExecutorService executor = Executors.newFixedThreadPool(2);
                try {
                        Future<?> confirmation = executor.submit(() -> paymentIntentService.withApplicantIntentLocked(
                                        121L,
                                        10L,
                                        lockedIntent -> {
                                                confirmationInsideLock.countDown();
                                                try {
                                                        if (!allowConfirmationToFinish.await(2, TimeUnit.SECONDS)) {
                                                                throw new IllegalStateException("confirmation latch timed out");
                                                        }
                                                        lockedIntent.setStatus(PaymentIntent.IntentStatus.APPLICATION_CREATED);
                                                        return null;
                                                } catch (InterruptedException e) {
                                                        Thread.currentThread().interrupt();
                                                        throw new IllegalStateException(e);
                                                } finally {
                                                        syntheticIntentRowLock.unlock();
                                                }
                                        }));

                        assertThat(confirmationInsideLock.await(2, TimeUnit.SECONDS)).isTrue();
                        Future<TossPaymentException> cancellation = executor.submit(() -> {
                                try {
                                        paymentIntentService.cancelPaymentIntent(121L, 10L, "racing cancellation");
                                        throw new AssertionError("expected application cancellation conflict");
                                } catch (TossPaymentException expected) {
                                        syntheticIntentRowLock.unlock();
                                        return expected;
                                }
                        });

                        assertThat(cancellationWaitingForLock.await(2, TimeUnit.SECONDS)).isTrue();
                        long queueDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
                        while (!syntheticIntentRowLock.hasQueuedThread(cancellationThread.get())
                                        && System.nanoTime() < queueDeadline) {
                                Thread.yield();
                        }
                        assertThat(syntheticIntentRowLock.hasQueuedThread(cancellationThread.get())).isTrue();
                        assertThat(cancellation.isDone()).isFalse();
                        allowConfirmationToFinish.countDown();

                        confirmation.get(2, TimeUnit.SECONDS);
                        assertThat(cancellation.get(2, TimeUnit.SECONDS).getMessage())
                                        .contains("신청 취소 경로");
                        assertThat(intent.getStatus()).isEqualTo(PaymentIntent.IntentStatus.APPLICATION_CREATED);
                        verify(tossPaymentService, never()).cancelPayment(any(), any(), anyInt());
                } finally {
                        allowConfirmationToFinish.countDown();
                        executor.shutdownNow();
                }
        }

        @Test
        void withConfirmedIntentForApplicationCreation_locksPartyBeforeApplication() {
                Long intentId = 122L;
                Long applicantId = 10L;
                Long partyId = 9L;
                String orderId = "MATE-1-10-confirmed-app-create-lock-order";
                String paymentKey = "pay-confirmed-app-create-lock-order";
                PaymentIntent intent = PaymentIntent.builder()
                                .id(intentId)
                                .orderId(orderId)
                                .partyId(partyId)
                                .applicantId(applicantId)
                                .paymentKey(paymentKey)
                                .expectedAmount(22000)
                                .status(PaymentIntent.IntentStatus.CONFIRMED)
                                .build();
                given(paymentIntentRepository.findByIdAndApplicantIdForUpdate(intentId, applicantId))
                                .willReturn(Optional.of(intent));
                given(partyRepository.findByIdForUpdate(partyId))
                                .willReturn(Optional.of(Party.builder().id(partyId).build()));
                given(applicationRepository.findByOrderIdForUpdate(orderId))
                                .willReturn(Optional.empty());

                String result = paymentIntentService.withConfirmedIntentForApplicationCreation(
                                intentId,
                                applicantId,
                                paymentKey,
                                ignored -> "created");

                assertThat(result).isEqualTo("created");
                assertThat(intent.getStatus()).isEqualTo(PaymentIntent.IntentStatus.APPLICATION_CREATED);
                var lockOrder = inOrder(paymentIntentRepository, partyRepository, applicationRepository);
                lockOrder.verify(paymentIntentRepository).findByIdAndApplicantIdForUpdate(intentId, applicantId);
                lockOrder.verify(partyRepository).findByIdForUpdate(partyId);
                lockOrder.verify(applicationRepository).findByOrderIdForUpdate(orderId);
        }

        @Test
        void reconciliationRequiredIntentCannotBeCanceledOrCompensatedAutomatically() {
                PaymentIntent intent = PaymentIntent.builder()
                                .id(131L)
                                .orderId("MATE-1-10-identity-mismatch-hold")
                                .applicantId(10L)
                                .paymentKey("synthetic-request-key")
                                .expectedAmount(22000)
                                .failureCode("PROVIDER_IDENTITY_MISMATCH")
                                .status(PaymentIntent.IntentStatus.RECONCILIATION_REQUIRED)
                                .build();

                given(paymentIntentRepository.findByIdAndApplicantIdForUpdate(131L, 10L))
                                .willReturn(Optional.of(intent));
                given(paymentIntentRepository.findByIdForUpdate(131L)).willReturn(Optional.of(intent));
                given(applicationRepository.findByOrderIdForUpdate(intent.getOrderId()))
                                .willReturn(Optional.empty());
                given(applicationRepository.findByOrderId(intent.getOrderId())).willReturn(Optional.empty());

                TossPaymentException cancellation = assertThrows(TossPaymentException.class,
                                () -> paymentIntentService.cancelPaymentIntent(131L, 10L, "synthetic cancellation"));
                assertThat(cancellation.getMessage()).contains("대사");

                paymentIntentService.compensateAfterApplicationFailure(131L,
                                new IllegalStateException("synthetic application failure"));
                paymentIntentService.retryCompensation(131L, 1);

                assertThat(intent.getStatus()).isEqualTo(PaymentIntent.IntentStatus.RECONCILIATION_REQUIRED);
                verify(tossPaymentService, never()).cancelPayment(any(), any(), anyInt());
                verify(paymentIntentRepository, never()).save(intent);
        }

        @Test
        void providerMismatchEvidenceIsBoundedBeforeItIsPersisted() {
                PaymentIntent intent = PaymentIntent.builder()
                                .id(132L)
                                .orderId("MATE-1-10-bounded-provider-evidence")
                                .expectedAmount(22000)
                                .failureCode("CONFIRM_REQUESTED")
                                .status(PaymentIntent.IntentStatus.PREPARED)
                                .build();
                TossPaymentDTO.ConfirmResponse providerResponse = new TossPaymentDTO.ConfirmResponse(
                                "p".repeat(230), "o".repeat(130), "S".repeat(40), 22000, null);

                paymentIntentService.quarantineProviderIdentityMismatch(
                                intent, "synthetic-request-key", providerResponse);

                ArgumentCaptor<PaymentIntentReconciliationEvent> eventCaptor =
                                ArgumentCaptor.forClass(PaymentIntentReconciliationEvent.class);
                verify(reconciliationEventRepository).save(eventCaptor.capture());
                PaymentIntentReconciliationEvent event = eventCaptor.getValue();
                assertThat(event.getCandidatePaymentKey()).hasSize(200).endsWith("...");
                assertThat(event.getCandidateOrderId()).hasSize(100).endsWith("...");
                assertThat(event.getCandidateStatus()).hasSize(30).endsWith("...");
                assertThat(intent.getStatus()).isEqualTo(PaymentIntent.IntentStatus.RECONCILIATION_REQUIRED);
        }

        @Test
        void cancelPaymentIntent_rejectsPreparedIntentWithUncertainConfirmationOutcome() {
                PaymentIntent intent = PaymentIntent.builder()
                                .id(118L)
                                .orderId("MATE-1-10-confirmation-uncertain")
                                .applicantId(10L)
                                .paymentKey("pay_confirmation_uncertain")
                                .expectedAmount(22000)
                                .failureCode("CONFIRM_REQUESTED")
                                .status(PaymentIntent.IntentStatus.PREPARED)
                                .build();

                given(paymentIntentRepository.findByIdAndApplicantIdForUpdate(118L, 10L))
                                .willReturn(java.util.Optional.of(intent));

                TossPaymentException exception = assertThrows(TossPaymentException.class,
                                () -> paymentIntentService.cancelPaymentIntent(118L, 10L, "cancel during confirmation"));

                assertThat(exception).hasMessageContaining("승인 결과를 확인 중");
                assertThat(intent.getStatus()).isEqualTo(PaymentIntent.IntentStatus.PREPARED);
                assertThat(intent.getPaymentKey()).isEqualTo("pay_confirmation_uncertain");
                verify(tossPaymentService, never()).cancelPayment(any(), any(), anyInt());
                verify(paymentIntentRepository, never()).save(intent);
        }

        @Test
        void cancelPaymentIntent_doesNotCompleteFromAlreadyCanceledErrorWithoutCancellationProof() {
                PaymentIntent intent = PaymentIntent.builder()
                                .id(116L)
                                .orderId("MATE-1-10-already-canceled-user")
                                .applicantId(10L)
                                .paymentKey("pay_already_canceled_user")
                                .expectedAmount(22000)
                                .status(PaymentIntent.IntentStatus.CONFIRMED)
                                .build();

                given(paymentIntentRepository.findByIdAndApplicantIdForUpdate(116L, 10L))
                                .willReturn(java.util.Optional.of(intent));
                given(tossPaymentService.cancelPayment(eq("pay_already_canceled_user"), any(), anyInt()))
                                .willThrow(new TossPaymentException(
                                                "결제 취소에 실패했습니다: 409 CONFLICT",
                                                HttpStatus.CONFLICT,
                                                "ALREADY_FULLY_CANCELED"));

                assertThrows(TossPaymentException.class,
                                () -> paymentIntentService.cancelPaymentIntent(116L, 10L, "test cancellation"));

                assertThat(intent.getStatus()).isEqualTo(PaymentIntent.IntentStatus.CANCEL_FAILED);
                assertThat(intent.getCanceledAt()).isNull();
                verify(paymentMetricsService).recordCompensation("user_cancel_fail");
        }

        @Test
        void cancelPaymentIntent_keepsFailureStateWhenProviderErrorIsReturned() throws Exception {
                Transactional transactional = PaymentIntentService.class
                                .getMethod("cancelPaymentIntent", Long.class, Long.class, String.class)
                                .getAnnotation(Transactional.class);

                assertThat(transactional.noRollbackFor()).contains(RuntimeException.class);
        }

        @Test
        void compensateAfterApplicationFailure_doesNotCompleteFromAlreadyCanceledErrorWithoutCancellationProof() {
                PaymentIntent intent = PaymentIntent.builder()
                                .id(105L)
                                .orderId("MATE-1-10-4000")
                                .applicantId(10L)
                                .paymentKey("pay_already_cancelled")
                                .expectedAmount(25000)
                                .status(PaymentIntent.IntentStatus.CONFIRMED)
                                .build();

                given(paymentIntentRepository.findByIdForUpdate(105L)).willReturn(java.util.Optional.of(intent));
                given(applicationRepository.findByOrderIdForUpdate("MATE-1-10-4000"))
                                .willReturn(java.util.Optional.empty());
                given(tossPaymentService.cancelPayment(eq("pay_already_cancelled"), any(), anyInt()))
                                .willThrow(new TossPaymentException(
                                                "결제 취소에 실패했습니다: 409 CONFLICT",
                                                HttpStatus.CONFLICT,
                                                "ALREADY_CANCELED_PAYMENT"));

                paymentIntentService.compensateAfterApplicationFailure(105L,
                                new IllegalStateException("payment failed"));

                assertThat(intent.getStatus()).isEqualTo(PaymentIntent.IntentStatus.CANCEL_FAILED);
                assertThat(intent.getCanceledAt()).isNull();
                verify(paymentMetricsService).recordCompensationRequested();
                verify(paymentMetricsService).recordCompensation("fail");
                verify(paymentIntentRepository, times(2)).save(intent);
                verify(jobScheduler).schedule(any(Instant.class), any(JobLambda.class));
        }

        // BUG-02 검증: 409이지만 에러 코드가 없고 메시지에 "취소"만 포함된 경우 CANCEL_FAILED로 기록(false
        // positive 방지)
        @Test
        void compensateAfterApplicationFailure_recordsFailureWhenCancelKeywordAloneIn409() {
                PaymentIntent intent = PaymentIntent.builder()
                                .id(106L)
                                .orderId("MATE-1-10-5000")
                                .applicantId(10L)
                                .paymentKey("pay_false_cancel")
                                .expectedAmount(25000)
                                .status(PaymentIntent.IntentStatus.CONFIRMED)
                                .build();

                // 에러 메시지에 "취소"만 포함되고, tossErrorCode가 없으며 status도 409인 경우
                // → isAlreadyCancelledByProvider가 false를 반환해야 함(false positive 없음)
                given(paymentIntentRepository.findByIdForUpdate(106L)).willReturn(java.util.Optional.of(intent));
                given(applicationRepository.findByOrderIdForUpdate("MATE-1-10-5000"))
                                .willReturn(java.util.Optional.empty());
                given(tossPaymentService.cancelPayment(eq("pay_false_cancel"), any(), anyInt()))
                                .willThrow(new TossPaymentException(
                                                "결제 취소에 실패했습니다: 409 CONFLICT",
                                                HttpStatus.CONFLICT));

                paymentIntentService.compensateAfterApplicationFailure(106L,
                                new IllegalStateException("payment failed"));

                assertThat(intent.getStatus()).isEqualTo(PaymentIntent.IntentStatus.CANCEL_FAILED);
                verify(paymentMetricsService).recordCompensation("fail");
                verify(jobScheduler).schedule(any(Instant.class), any(JobLambda.class));
        }

        @Test
        void compensateAfterApplicationFailure_doesNotCompleteFromAlreadyFullyCanceledErrorWithoutCancellationProof() {
                PaymentIntent intent = PaymentIntent.builder()
                                .id(107L)
                                .orderId("MATE-1-10-6000")
                                .applicantId(10L)
                                .paymentKey("pay_fully_cancelled")
                                .expectedAmount(25000)
                                .status(PaymentIntent.IntentStatus.CONFIRMED)
                                .build();

                given(paymentIntentRepository.findByIdForUpdate(107L)).willReturn(java.util.Optional.of(intent));
                given(applicationRepository.findByOrderIdForUpdate("MATE-1-10-6000"))
                                .willReturn(java.util.Optional.empty());
                given(tossPaymentService.cancelPayment(eq("pay_fully_cancelled"), any(), anyInt()))
                                .willThrow(new TossPaymentException(
                                                "결제 취소에 실패했습니다: 409 CONFLICT",
                                                HttpStatus.CONFLICT,
                                                "ALREADY_FULLY_CANCELED"));

                paymentIntentService.compensateAfterApplicationFailure(107L,
                                new IllegalStateException("payment failed"));

                assertThat(intent.getStatus()).isEqualTo(PaymentIntent.IntentStatus.CANCEL_FAILED);
                assertThat(intent.getCanceledAt()).isNull();
                verify(paymentMetricsService).recordCompensation("fail");
                verify(jobScheduler).schedule(any(Instant.class), any(JobLambda.class));
        }

        @Test
        void reconcileCompensationTargets_delegatesToReconciliationService() {
                PaymentIntent targetA = PaymentIntent.builder()
                                .id(108L)
                                .orderId("MATE-1-10-7000")
                                .build();
                PaymentIntent targetB = PaymentIntent.builder()
                                .id(109L)
                                .orderId("MATE-1-10-7001")
                                .build();

                given(paymentIntentRepository.findByStatusInAndUpdatedAtBefore(
                                anySet(),
                                any(Instant.class)))
                                .willReturn(List.of(targetA, targetB));

                paymentIntentService.reconcileCompensationTargets();

                verify(paymentIntentRepository).findByStatusInAndUpdatedAtBefore(
                                anySet(),
                                any(Instant.class));
                verify(paymentIntentReconciliationService).reconcileSingleIntent(108L);
                verify(paymentIntentReconciliationService).reconcileSingleIntent(109L);
        }

        @Test
        void reconcileCompensationTargets_continuesWhenSingleTargetFails() {
                PaymentIntent targetA = PaymentIntent.builder()
                                .id(110L)
                                .orderId("MATE-1-10-7002")
                                .build();
                PaymentIntent targetB = PaymentIntent.builder()
                                .id(111L)
                                .orderId("MATE-1-10-7003")
                                .build();

                given(paymentIntentRepository.findByStatusInAndUpdatedAtBefore(
                                anySet(),
                                any(Instant.class)))
                                .willReturn(List.of(targetA, targetB));

                doThrow(new RuntimeException("reconcile failed"))
                                .when(paymentIntentReconciliationService).reconcileSingleIntent(110L);

                assertDoesNotThrow(() -> paymentIntentService.reconcileCompensationTargets());

                verify(paymentIntentReconciliationService).reconcileSingleIntent(110L);
                verify(paymentIntentReconciliationService).reconcileSingleIntent(111L);
        }

        @Test
        void cancelPaymentIntent_doesNotLogRawPaymentKey() {
                String paymentKey = "payment-intent-sensitive-sentinel";
                PaymentIntent intent = PaymentIntent.builder()
                                .id(130L)
                                .orderId("MATE-1-10-log-redaction")
                                .applicantId(10L)
                                .paymentKey(paymentKey)
                                .expectedAmount(22000)
                                .status(PaymentIntent.IntentStatus.CONFIRMED)
                                .build();
                TossPaymentDTO.CancelResponse response = new TossPaymentDTO.CancelResponse(
                                paymentKey, "CANCELED", 22000);
                response.setCancels(List.of(new TossPaymentDTO.CancelDetail(22000, "DONE", null, null)));

                given(paymentIntentRepository.findByIdAndApplicantIdForUpdate(130L, 10L))
                                .willReturn(Optional.of(intent));
                given(applicationRepository.findByOrderIdForUpdate("MATE-1-10-log-redaction"))
                                .willReturn(Optional.empty());
                given(tossPaymentService.cancelPayment(eq(paymentKey), any(), anyInt())).willReturn(response);

                Logger logger = (Logger) LoggerFactory.getLogger(PaymentIntentService.class);
                ListAppender<ILoggingEvent> appender = new ListAppender<>();
                appender.start();
                logger.addAppender(appender);
                try {
                        assertThat(paymentIntentService.cancelPaymentIntent(130L, 10L, "test cancellation"))
                                        .isEqualTo(PaymentIntent.IntentStatus.CANCELED);
                        assertThat(appender.list)
                                        .extracting(ILoggingEvent::getFormattedMessage)
                                        .noneMatch(message -> message.contains(paymentKey));
                } finally {
                        logger.detachAppender(appender);
                        appender.stop();
                }
        }
}
