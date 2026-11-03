package com.example.mate.controller;

import com.example.mate.dto.PartyApplicationDTO;
import com.example.mate.dto.TossPaymentDTO;
import com.example.common.exception.BadRequestBusinessException;
import com.example.mate.entity.PartyApplication;
import com.example.mate.entity.PaymentFlowType;
import com.example.mate.entity.PaymentIntent;
import com.example.mate.entity.PaymentStatus;
import com.example.mate.entity.PaymentTransaction;
import com.example.mate.exception.TossPaymentException;
import com.example.mate.service.PartyApplicationService;
import com.example.mate.service.PaymentIntentService;
import com.example.mate.service.PaymentMetricsService;
import com.example.mate.service.MatePaymentModeService;
import com.example.mate.service.PaymentTransactionService;
import com.example.mate.service.TossPaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    @Mock
    private TossPaymentService tossPaymentService;
    @Mock
    private PartyApplicationService applicationService;
    @Mock
    private PaymentIntentService paymentIntentService;
    @Mock
    private PaymentTransactionService paymentTransactionService;
    @Mock
    private PaymentMetricsService paymentMetricsService;
    @Mock
    private MatePaymentModeService matePaymentModeService;

    @InjectMocks
    private PaymentController paymentController;

    @BeforeEach
    void setUp() {
        lenient().when(matePaymentModeService.isDirectTrade()).thenReturn(false);
        lenient().when(matePaymentModeService.isTossPaymentEnabled()).thenReturn(true);
        lenient().when(paymentIntentService.withApplicantConfirmationSerialized(any(Long.class), any(Supplier.class)))
                .thenAnswer(invocation -> invocation.<Supplier<Object>>getArgument(1).get());
    }

    @Test
    void confirmTossPayment_usesServerAmountAndForcesDepositType() {
        Long userId = 10L;
        String ticketImageUrl = "x".repeat(2048);

        TossPaymentDTO.ClientConfirmRequest request = TossPaymentDTO.ClientConfirmRequest.builder()
                .paymentKey("payment_key")
                .orderId("MATE-1-10-1708500000000")
                .partyId(1L)
                .intentId(10L)
                .paymentType(PartyApplication.PaymentType.DEPOSIT)
                .message(" ")
                .ticketImageUrl(ticketImageUrl)
                .build();

        PaymentIntent intent = PaymentIntent.builder()
                .id(10L)
                .orderId("MATE-1-10-1708500000000")
                .partyId(1L)
                .applicantId(10L)
                .expectedAmount(35000)
                .status(PaymentIntent.IntentStatus.PREPARED)
                .paymentType(PartyApplication.PaymentType.DEPOSIT)
                .currency("KRW")
                .build();

        TossPaymentDTO.ConfirmResponse tossResponse = new TossPaymentDTO.ConfirmResponse(
                "payment_key", "MATE-1-10-1708500000000", "DONE", 35000, "카드");

        PartyApplicationDTO.Response created = PartyApplicationDTO.Response.builder()
                .id(99L)
                .partyId(1L)
                .depositAmount(35000)
                .paymentType(PartyApplication.PaymentType.DEPOSIT)
                .build();

        given(paymentIntentService.findExistingApplicationResponse("MATE-1-10-1708500000000", 10L))
                .willReturn(Optional.empty());
        given(paymentIntentService.resolveIntentForConfirm(request, userId)).willReturn(intent);
        given(paymentIntentService.reserveConfirmationAttempt(10L, userId, "payment_key")).willReturn(false);
        stubIntentLocks(intent);
        given(tossPaymentService.confirmPayment("payment_key", "MATE-1-10-1708500000000", 35000)).willReturn(tossResponse);
        given(applicationService.createOrGetApplicationWithPayment(
                any(PartyApplicationDTO.Request.class),
                eq(userId),
                eq("payment_key"),
                eq("MATE-1-10-1708500000000"),
                eq(35000),
                eq(PartyApplication.PaymentType.DEPOSIT)))
                .willReturn(new PartyApplicationService.PaymentCreationResult(created, true));
        given(applicationService.getApplicationEntity(99L))
                .willReturn(PartyApplication.builder().id(99L).orderId("MATE-1-10-1708500000000").build());
        given(paymentTransactionService.createOrGetOnConfirm(any(PartyApplication.class), eq(intent), eq("payment_key")))
                .willReturn(PaymentTransaction.builder().id(1L).build());

        var response = paymentController.confirmTossPayment(request, userId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(created);

        ArgumentCaptor<PartyApplicationDTO.Request> appReqCaptor = ArgumentCaptor.forClass(PartyApplicationDTO.Request.class);
        verify(applicationService).createOrGetApplicationWithPayment(
                appReqCaptor.capture(),
                eq(userId),
                eq("payment_key"),
                eq("MATE-1-10-1708500000000"),
                eq(35000),
                eq(PartyApplication.PaymentType.DEPOSIT));
        verify(paymentTransactionService).createOrGetOnConfirm(any(PartyApplication.class), eq(intent), eq("payment_key"));
        verify(paymentTransactionService).enrichResponse(created);

        PartyApplicationDTO.Request appRequest = appReqCaptor.getValue();
        assertThat(appRequest.getDepositAmount()).isEqualTo(35000);
        assertThat(appRequest.getPaymentType()).isEqualTo(PartyApplication.PaymentType.DEPOSIT);
        assertThat(appRequest.getMessage()).isEqualTo("함께 즐거운 관람 부탁드립니다!");
        assertThat(appRequest.getTicketImageUrl()).isEqualTo(ticketImageUrl);

        verify(tossPaymentService).confirmPayment("payment_key", "MATE-1-10-1708500000000", 35000);
    }

    @Test
    void confirmTossPayment_quarantinesProviderIdentityMismatchWithoutCreatingPaymentRecords() {
        Long userId = 10L;
        String orderId = "MATE-1-10-identity-mismatch";
        TossPaymentDTO.ClientConfirmRequest request = TossPaymentDTO.ClientConfirmRequest.builder()
                .paymentKey("synthetic-request-key")
                .orderId(orderId)
                .partyId(1L)
                .intentId(10L)
                .paymentType(PartyApplication.PaymentType.DEPOSIT)
                .build();
        PaymentIntent intent = PaymentIntent.builder()
                .id(10L)
                .orderId(orderId)
                .partyId(1L)
                .applicantId(userId)
                .expectedAmount(35000)
                .status(PaymentIntent.IntentStatus.PREPARED)
                .paymentType(PartyApplication.PaymentType.DEPOSIT)
                .currency("KRW")
                .build();
        TossPaymentDTO.ConfirmResponse mismatch = new TossPaymentDTO.ConfirmResponse(
                "synthetic-provider-key", "synthetic-other-order", "DONE", 35000, "카드");

        given(paymentIntentService.findExistingApplicationResponse(orderId, userId)).willReturn(Optional.empty());
        given(paymentIntentService.resolveIntentForConfirm(request, userId)).willReturn(intent);
        stubIntentLocks(intent);
        given(tossPaymentService.confirmPayment("synthetic-request-key", orderId, 35000)).willReturn(mismatch);
        org.mockito.Mockito.doAnswer(invocation -> {
            intent.setStatus(PaymentIntent.IntentStatus.RECONCILIATION_REQUIRED);
            intent.setFailureCode("PROVIDER_IDENTITY_MISMATCH");
            return null;
        }).when(paymentIntentService).quarantineProviderIdentityMismatch(intent, "synthetic-request-key", mismatch);

        assertThatThrownBy(() -> paymentController.confirmTossPayment(request, userId))
                .isInstanceOf(TossPaymentException.class)
                .hasMessageContaining("확인")
                .extracting("code")
                .isEqualTo("TOSS_CONFIRM_IDENTITY_MISMATCH_RECONCILIATION_REQUIRED");

        assertThat(intent.getStatus()).isEqualTo(PaymentIntent.IntentStatus.RECONCILIATION_REQUIRED);
        verify(paymentIntentService).quarantineProviderIdentityMismatch(intent, "synthetic-request-key", mismatch);
        verify(paymentIntentService, never()).markConfirmed(any(PaymentIntent.class), any(String.class));
        verify(applicationService, never()).createOrGetApplicationWithPayment(
                any(PartyApplicationDTO.Request.class), any(Long.class), any(String.class), any(String.class),
                anyInt(), any(PartyApplication.PaymentType.class));
        verify(paymentTransactionService, never()).createOrGetOnConfirm(
                any(PartyApplication.class), any(PaymentIntent.class), any(String.class));
        verify(paymentIntentService, never()).compensateAfterApplicationFailure(
                any(Long.class), any(RuntimeException.class));
    }

    @Test
    void confirmTossPayment_recoversAmbiguousAttemptFromExactProviderStatusWithoutConfirmingAgain() {
        Long userId = 10L;
        String orderId = "MATE-1-10-ambiguous-recovery";
        String paymentKey = "synthetic-ambiguous-payment-key";
        TossPaymentDTO.ClientConfirmRequest request = TossPaymentDTO.ClientConfirmRequest.builder()
                .paymentKey(paymentKey)
                .orderId(orderId)
                .partyId(1L)
                .intentId(10L)
                .paymentType(PartyApplication.PaymentType.DEPOSIT)
                .build();
        PaymentIntent intent = stubAmbiguousConfirmRequestedIntent(request, userId);
        TossPaymentDTO.ConfirmResponse providerStatus = new TossPaymentDTO.ConfirmResponse(
                paymentKey, orderId, "DONE", 35000, "카드");
        PartyApplicationDTO.Response created = PartyApplicationDTO.Response.builder()
                .id(99L)
                .partyId(1L)
                .depositAmount(35000)
                .paymentType(PartyApplication.PaymentType.DEPOSIT)
                .build();
        given(tossPaymentService.getPayment(paymentKey)).willReturn(providerStatus);
        given(applicationService.createOrGetApplicationWithPayment(
                any(PartyApplicationDTO.Request.class), eq(userId), eq(paymentKey), eq(orderId), eq(35000),
                eq(PartyApplication.PaymentType.DEPOSIT)))
                .willReturn(new PartyApplicationService.PaymentCreationResult(created, true));
        given(applicationService.getApplicationEntity(99L))
                .willReturn(PartyApplication.builder().id(99L).orderId(orderId).build());
        given(paymentTransactionService.createOrGetOnConfirm(any(PartyApplication.class), eq(intent), eq(paymentKey)))
                .willReturn(PaymentTransaction.builder().id(1L).build());

        var response = paymentController.confirmTossPayment(request, userId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(created);
        verify(tossPaymentService).getPayment(paymentKey);
        verify(tossPaymentService, never()).confirmPayment(any(), any(), anyInt());
        verify(paymentIntentService).markConfirmed(intent, paymentKey);
        verify(applicationService).createOrGetApplicationWithPayment(
                any(PartyApplicationDTO.Request.class), eq(userId), eq(paymentKey), eq(orderId), eq(35000),
                eq(PartyApplication.PaymentType.DEPOSIT));
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "pending", "lowercase-done", "payment-key-mismatch", "order-id-mismatch", "amount-mismatch"})
    void confirmTossPayment_holdsAmbiguousAttemptWhenProviderStatusIsNotAnExactDoneTuple(String responseKind) {
        Long userId = 10L;
        String orderId = "MATE-1-10-ambiguous-hold";
        String paymentKey = "synthetic-ambiguous-payment-key";
        TossPaymentDTO.ClientConfirmRequest request = TossPaymentDTO.ClientConfirmRequest.builder()
                .paymentKey(paymentKey)
                .orderId(orderId)
                .partyId(1L)
                .intentId(10L)
                .paymentType(PartyApplication.PaymentType.DEPOSIT)
                .build();
        PaymentIntent intent = stubAmbiguousConfirmRequestedIntent(request, userId);
        TossPaymentDTO.ConfirmResponse providerStatus = switch (responseKind) {
            case "missing" -> null;
            case "pending" -> new TossPaymentDTO.ConfirmResponse(paymentKey, orderId, "WAITING_FOR_DEPOSIT", 35000, "카드");
            case "lowercase-done" -> new TossPaymentDTO.ConfirmResponse(paymentKey, orderId, "done", 35000, "카드");
            case "payment-key-mismatch" -> new TossPaymentDTO.ConfirmResponse("synthetic-other-key", orderId, "DONE", 35000, "카드");
            case "order-id-mismatch" -> new TossPaymentDTO.ConfirmResponse(paymentKey, "MATE-OTHER-ORDER", "DONE", 35000, "카드");
            case "amount-mismatch" -> new TossPaymentDTO.ConfirmResponse(paymentKey, orderId, "DONE", 36000, "카드");
            default -> throw new IllegalArgumentException("Unexpected response kind: " + responseKind);
        };
        given(tossPaymentService.getPayment(paymentKey)).willReturn(providerStatus);

        assertThatThrownBy(() -> paymentController.confirmTossPayment(request, userId))
                .isInstanceOfSatisfying(TossPaymentException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception).extracting("code").isEqualTo("PAYMENT_INTENT_RECONCILIATION_REQUIRED");
                });

        verify(tossPaymentService).getPayment(paymentKey);
        verify(tossPaymentService, never()).confirmPayment(any(), any(), anyInt());
        verify(paymentIntentService).quarantineUnverifiedConfirmation(intent, paymentKey, providerStatus);
        verify(applicationService, never()).createOrGetApplicationWithPayment(
                any(PartyApplicationDTO.Request.class), any(Long.class), any(), any(), anyInt(), any());
        verify(paymentIntentService, never()).markConfirmed(any(PaymentIntent.class), any(String.class));
    }

    @Test
    void confirmTossPayment_holdsAmbiguousAttemptWhenProviderStatusLookupFails() {
        Long userId = 10L;
        String orderId = "MATE-1-10-ambiguous-lookup-failure";
        String paymentKey = "synthetic-ambiguous-payment-key";
        TossPaymentDTO.ClientConfirmRequest request = TossPaymentDTO.ClientConfirmRequest.builder()
                .paymentKey(paymentKey)
                .orderId(orderId)
                .partyId(1L)
                .intentId(10L)
                .paymentType(PartyApplication.PaymentType.DEPOSIT)
                .build();
        PaymentIntent intent = stubAmbiguousConfirmRequestedIntent(request, userId);
        given(tossPaymentService.getPayment(paymentKey))
                .willThrow(new TossPaymentException(
                        "SYNTHETIC_LOOKUP_FAILURE", "synthetic provider lookup failure", HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(() -> paymentController.confirmTossPayment(request, userId))
                .isInstanceOfSatisfying(TossPaymentException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception).extracting("code").isEqualTo("PAYMENT_INTENT_RECONCILIATION_REQUIRED");
                });

        verify(tossPaymentService).getPayment(paymentKey);
        verify(tossPaymentService, never()).confirmPayment(any(), any(), anyInt());
        verify(paymentIntentService).quarantineUnverifiedConfirmation(intent, paymentKey, null);
        verify(applicationService, never()).createOrGetApplicationWithPayment(
                any(PartyApplicationDTO.Request.class), any(Long.class), any(), any(), anyInt(), any());
    }

    @Test
    void confirmTossPayment_blocksRepeatConfirmationForMismatchHold() {
        Long userId = 10L;
        String orderId = "MATE-1-10-identity-mismatch-retry";
        TossPaymentDTO.ClientConfirmRequest request = TossPaymentDTO.ClientConfirmRequest.builder()
                .paymentKey("synthetic-request-key")
                .orderId(orderId)
                .partyId(1L)
                .intentId(11L)
                .paymentType(PartyApplication.PaymentType.DEPOSIT)
                .build();
        PaymentIntent intent = PaymentIntent.builder()
                .id(11L)
                .orderId(orderId)
                .partyId(1L)
                .applicantId(userId)
                .expectedAmount(35000)
                .status(PaymentIntent.IntentStatus.RECONCILIATION_REQUIRED)
                .failureCode("PROVIDER_IDENTITY_MISMATCH")
                .paymentType(PartyApplication.PaymentType.DEPOSIT)
                .currency("KRW")
                .build();
        given(paymentIntentService.findExistingApplicationResponse(orderId, userId)).willReturn(Optional.empty());
        given(paymentIntentService.resolveIntentForConfirm(request, userId)).willReturn(intent);
        stubIntentLocks(intent);

        assertThatThrownBy(() -> paymentController.confirmTossPayment(request, userId))
                .isInstanceOf(TossPaymentException.class)
                .extracting("code")
                .isEqualTo("PAYMENT_INTENT_RECONCILIATION_REQUIRED");

        verify(tossPaymentService, never()).confirmPayment(any(), any(), any());
        verify(paymentIntentService, never()).quarantineProviderIdentityMismatch(
                any(PaymentIntent.class), any(String.class), any(TossPaymentDTO.ConfirmResponse.class));
    }

    @Test
    void confirmTossPayment_resumesAdminVerifiedPaymentWithoutCallingProviderAgain() {
        Long userId = 10L;
        Long intentId = 12L;
        String orderId = "MATE-1-10-verified-pending-application";
        TossPaymentDTO.ClientConfirmRequest request = TossPaymentDTO.ClientConfirmRequest.builder()
                .paymentKey("synthetic-original-request-key")
                .orderId(orderId)
                .partyId(1L)
                .intentId(intentId)
                .paymentType(PartyApplication.PaymentType.DEPOSIT)
                .message("synthetic retry after operator verification")
                .build();
        PaymentIntent intent = PaymentIntent.builder()
                .id(intentId)
                .orderId(orderId)
                .partyId(1L)
                .applicantId(userId)
                .paymentKey("synthetic-admin-verified-key")
                .expectedAmount(35000)
                .status(PaymentIntent.IntentStatus.RECONCILIATION_REQUIRED)
                .failureCode("VERIFIED_PAYMENT_PENDING_APPLICATION")
                .paymentType(PartyApplication.PaymentType.DEPOSIT)
                .currency("KRW")
                .build();
        PartyApplicationDTO.Response created = PartyApplicationDTO.Response.builder()
                .id(120L)
                .partyId(1L)
                .depositAmount(35000)
                .paymentType(PartyApplication.PaymentType.DEPOSIT)
                .build();

        given(paymentIntentService.findExistingApplicationResponse(orderId, userId)).willReturn(Optional.empty());
        given(paymentIntentService.resolveIntentForConfirm(request, userId)).willReturn(intent);
        given(paymentIntentService.hasVerifiedIdentityPaymentBinding(intentId)).willReturn(true);
        given(paymentIntentService.isVerifiedIdentityResolutionRetry(intentId, "synthetic-original-request-key"))
                .willReturn(true);
        stubIntentLocks(intent);
        given(applicationService.createOrGetApplicationWithPayment(
                any(PartyApplicationDTO.Request.class),
                eq(userId),
                eq("synthetic-admin-verified-key"),
                eq(orderId),
                eq(35000),
                eq(PartyApplication.PaymentType.DEPOSIT)))
                .willReturn(new PartyApplicationService.PaymentCreationResult(created, true));
        given(applicationService.getApplicationEntity(120L))
                .willReturn(PartyApplication.builder().id(120L).orderId(orderId).build());

        var response = paymentController.confirmTossPayment(request, userId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(created);
        verify(tossPaymentService, never()).confirmPayment(any(), any(), any());
        verify(applicationService).createOrGetApplicationWithPayment(
                any(PartyApplicationDTO.Request.class), eq(userId), eq("synthetic-admin-verified-key"),
                eq(orderId), eq(35000), eq(PartyApplication.PaymentType.DEPOSIT));
        verify(paymentIntentService).withConfirmedIntentForApplicationCreation(
                eq(intentId), eq(userId), eq("synthetic-admin-verified-key"), any(Function.class));
    }

    @Test
    void confirmTossPayment_preflightsApplicationInputBeforeResumingAdminVerifiedPayment() {
        Long userId = 10L;
        Long intentId = 13L;
        String orderId = "MATE-1-10-verified-pending-preflight";
        TossPaymentDTO.ClientConfirmRequest request = TossPaymentDTO.ClientConfirmRequest.builder()
                .paymentKey("synthetic-original-request-key")
                .orderId(orderId)
                .partyId(1L)
                .intentId(intentId)
                .paymentType(PartyApplication.PaymentType.DEPOSIT)
                .message("x".repeat(501))
                .build();
        PaymentIntent intent = PaymentIntent.builder()
                .id(intentId)
                .orderId(orderId)
                .partyId(1L)
                .applicantId(userId)
                .paymentKey("synthetic-admin-verified-key")
                .expectedAmount(35000)
                .status(PaymentIntent.IntentStatus.RECONCILIATION_REQUIRED)
                .failureCode("VERIFIED_PAYMENT_PENDING_APPLICATION")
                .paymentType(PartyApplication.PaymentType.DEPOSIT)
                .currency("KRW")
                .build();

        given(paymentIntentService.findExistingApplicationResponse(orderId, userId)).willReturn(Optional.empty());
        given(paymentIntentService.resolveIntentForConfirm(request, userId)).willReturn(intent);
        given(paymentIntentService.hasVerifiedIdentityPaymentBinding(intentId)).willReturn(true);
        given(paymentIntentService.isVerifiedIdentityResolutionRetry(intentId, "synthetic-original-request-key"))
                .willReturn(true);

        assertThatThrownBy(() -> paymentController.confirmTossPayment(request, userId))
                .isInstanceOf(BadRequestBusinessException.class)
                .hasMessageContaining("500자");
        verify(paymentIntentService, never()).reserveConfirmationAttempt(any(), any(), any());
        verify(tossPaymentService, never()).confirmPayment(any(), any(), any());
    }

    @Test
    void confirmTossPayment_preflightsInvalidMessageBeforeResumingConfirmedIntent() {
        Long userId = 10L;
        Long intentId = 14L;
        String orderId = "MATE-1-10-confirmed-pending-message-preflight";
        String confirmedPaymentKey = "synthetic-confirmed-payment-key";
        TossPaymentDTO.ClientConfirmRequest request = TossPaymentDTO.ClientConfirmRequest.builder()
                .paymentKey(confirmedPaymentKey)
                .orderId(orderId)
                .partyId(1L)
                .intentId(intentId)
                .paymentType(PartyApplication.PaymentType.DEPOSIT)
                .message("x".repeat(501))
                .build();
        PaymentIntent intent = PaymentIntent.builder()
                .id(intentId)
                .orderId(orderId)
                .partyId(1L)
                .applicantId(userId)
                .paymentKey(confirmedPaymentKey)
                .expectedAmount(35000)
                .status(PaymentIntent.IntentStatus.CONFIRMED)
                .paymentType(PartyApplication.PaymentType.DEPOSIT)
                .currency("KRW")
                .build();

        given(paymentIntentService.findExistingApplicationResponse(orderId, userId)).willReturn(Optional.empty());
        given(paymentIntentService.resolveIntentForConfirm(request, userId)).willReturn(intent);
        stubIntentLocks(intent);

        assertThatThrownBy(() -> paymentController.confirmTossPayment(request, userId))
                .isInstanceOfSatisfying(BadRequestBusinessException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getCode()).isEqualTo("INVALID_APPLICATION_MESSAGE");
                    assertThat(exception).hasMessageContaining("500자");
                });

        assertThat(intent.getStatus()).isEqualTo(PaymentIntent.IntentStatus.CONFIRMED);
        assertThat(intent.getPaymentKey()).isEqualTo(confirmedPaymentKey);
        verifyNoPaymentConfirmationSideEffects();
        verify(paymentTransactionService, never()).createOrGetOnConfirm(
                any(PartyApplication.class), any(PaymentIntent.class), any(String.class));
    }

    @Test
    void confirmTossPayment_preflightsInvalidTicketUrlBeforeResumingConfirmedIntent() {
        Long userId = 10L;
        Long intentId = 15L;
        String orderId = "MATE-1-10-confirmed-pending-url-preflight";
        String confirmedPaymentKey = "synthetic-confirmed-url-payment-key";
        TossPaymentDTO.ClientConfirmRequest request = TossPaymentDTO.ClientConfirmRequest.builder()
                .paymentKey(confirmedPaymentKey)
                .orderId(orderId)
                .partyId(1L)
                .intentId(intentId)
                .paymentType(PartyApplication.PaymentType.DEPOSIT)
                .ticketImageUrl("x".repeat(2049))
                .build();
        PaymentIntent intent = PaymentIntent.builder()
                .id(intentId)
                .orderId(orderId)
                .partyId(1L)
                .applicantId(userId)
                .paymentKey(confirmedPaymentKey)
                .expectedAmount(35000)
                .status(PaymentIntent.IntentStatus.CONFIRMED)
                .paymentType(PartyApplication.PaymentType.DEPOSIT)
                .currency("KRW")
                .build();

        given(paymentIntentService.findExistingApplicationResponse(orderId, userId)).willReturn(Optional.empty());
        given(paymentIntentService.resolveIntentForConfirm(request, userId)).willReturn(intent);
        stubIntentLocks(intent);

        assertThatThrownBy(() -> paymentController.confirmTossPayment(request, userId))
                .isInstanceOfSatisfying(BadRequestBusinessException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getCode()).isEqualTo("INVALID_TICKET_IMAGE_URL");
                    assertThat(exception).hasMessageContaining("2048자");
                });

        assertThat(intent.getStatus()).isEqualTo(PaymentIntent.IntentStatus.CONFIRMED);
        assertThat(intent.getPaymentKey()).isEqualTo(confirmedPaymentKey);
        verifyNoPaymentConfirmationSideEffects();
        verify(paymentTransactionService, never()).createOrGetOnConfirm(
                any(PartyApplication.class), any(PaymentIntent.class), any(String.class));
    }

    @Test
    void confirmTossPayment_resumesConfirmedIntentWithValidInputAndSamePaymentKey() {
        Long userId = 10L;
        Long intentId = 16L;
        String orderId = "MATE-1-10-confirmed-pending-valid-input";
        String confirmedPaymentKey = "synthetic-valid-confirmed-payment-key";
        TossPaymentDTO.ClientConfirmRequest request = TossPaymentDTO.ClientConfirmRequest.builder()
                .paymentKey(confirmedPaymentKey)
                .orderId(orderId)
                .partyId(1L)
                .intentId(intentId)
                .paymentType(PartyApplication.PaymentType.DEPOSIT)
                .message("Valid retry message")
                .build();
        PaymentIntent intent = PaymentIntent.builder()
                .id(intentId)
                .orderId(orderId)
                .partyId(1L)
                .applicantId(userId)
                .paymentKey(confirmedPaymentKey)
                .expectedAmount(35000)
                .status(PaymentIntent.IntentStatus.CONFIRMED)
                .paymentType(PartyApplication.PaymentType.DEPOSIT)
                .currency("KRW")
                .build();
        PartyApplicationDTO.Response created = PartyApplicationDTO.Response.builder()
                .id(160L)
                .partyId(1L)
                .depositAmount(35000)
                .paymentType(PartyApplication.PaymentType.DEPOSIT)
                .build();

        given(paymentIntentService.findExistingApplicationResponse(orderId, userId)).willReturn(Optional.empty());
        given(paymentIntentService.resolveIntentForConfirm(request, userId)).willReturn(intent);
        stubIntentLocks(intent);
        given(applicationService.createOrGetApplicationWithPayment(
                any(PartyApplicationDTO.Request.class),
                eq(userId),
                eq(confirmedPaymentKey),
                eq(orderId),
                eq(35000),
                eq(PartyApplication.PaymentType.DEPOSIT)))
                .willReturn(new PartyApplicationService.PaymentCreationResult(created, true));
        given(applicationService.getApplicationEntity(160L))
                .willReturn(PartyApplication.builder().id(160L).orderId(orderId).build());

        var response = paymentController.confirmTossPayment(request, userId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(created);
        verify(tossPaymentService, never()).confirmPayment(any(), any(), any());
        verify(applicationService).createOrGetApplicationWithPayment(
                any(PartyApplicationDTO.Request.class), eq(userId), eq(confirmedPaymentKey),
                eq(orderId), eq(35000), eq(PartyApplication.PaymentType.DEPOSIT));
        verify(paymentIntentService, never()).compensateAfterApplicationFailure(
                any(Long.class), any(RuntimeException.class));
    }

    @Test
    void confirmTossPayment_returnsExistingApplicationForIdempotentRetry() {
        Long userId = 10L;

        TossPaymentDTO.ClientConfirmRequest request = TossPaymentDTO.ClientConfirmRequest.builder()
                .paymentKey("pk_test")
                .orderId("MATE-1-10-1708500000000")
                .partyId(1L)
                .message("short")
                .ticketImageUrl("x".repeat(2049))
                .build();

        PartyApplicationDTO.Response existing = PartyApplicationDTO.Response.builder()
                .id(100L)
                .partyId(1L)
                .paymentType(PartyApplication.PaymentType.DEPOSIT)
                .build();

        given(paymentIntentService.findExistingApplicationResponse("MATE-1-10-1708500000000", 10L))
                .willReturn(Optional.of(existing));
        given(paymentIntentService.findIntentByOrderId("MATE-1-10-1708500000000"))
                .willReturn(Optional.of(PaymentIntent.builder()
                        .id(10L)
                        .orderId("MATE-1-10-1708500000000")
                        .partyId(1L)
                        .applicantId(10L)
                        .paymentKey("pk_test")
                        .status(PaymentIntent.IntentStatus.APPLICATION_CREATED)
                        .build()));
        PaymentIntent existingIntent = paymentIntentService.findIntentByOrderId("MATE-1-10-1708500000000")
                .orElseThrow();
        stubIntentLocks(existingIntent);
        given(paymentTransactionService.findByOrderId("MATE-1-10-1708500000000"))
                .willReturn(Optional.of(PaymentTransaction.builder()
                        .orderId("MATE-1-10-1708500000000")
                        .paymentKey("pk_test")
                        .paymentStatus(PaymentStatus.PAID)
                        .build()));
        given(applicationService.getApplicationEntity(100L))
                .willReturn(PartyApplication.builder()
                        .id(100L)
                        .orderId("MATE-1-10-1708500000000")
                        .paymentKey("pk_test")
                        .build());

        var response = paymentController.confirmTossPayment(request, userId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(existing);

        verify(paymentIntentService, never())
                .resolveIntentForConfirm(any(TossPaymentDTO.ClientConfirmRequest.class), any(Long.class));
        verify(tossPaymentService, never()).confirmPayment(any(), any(), anyInt());
    }

    @Test
    void confirmTossPayment_rejectsInvalidApplicationMessageBeforeProviderConfirmation() {
        Long userId = 10L;
        TossPaymentDTO.ClientConfirmRequest request = preflightRequest("short", null);
        stubFreshPreparedIntent(request, userId);

        assertThatThrownBy(() -> paymentController.confirmTossPayment(request, userId))
                .isInstanceOfSatisfying(BadRequestBusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("INVALID_APPLICATION_MESSAGE"));

        verifyNoPaymentConfirmationSideEffects();
    }

    @Test
    void confirmTossPayment_rejectsOverlongUntrimmedMessageBeforeProviderConfirmation() {
        Long userId = 10L;
        String message = "유효한 신청 메시지입니다." + " ".repeat(500);
        TossPaymentDTO.ClientConfirmRequest request = preflightRequest(message, null);
        stubFreshPreparedIntent(request, userId);

        assertThatThrownBy(() -> paymentController.confirmTossPayment(request, userId))
                .isInstanceOfSatisfying(BadRequestBusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("INVALID_APPLICATION_MESSAGE"));

        verifyNoPaymentConfirmationSideEffects();
    }

    @Test
    void confirmTossPayment_rejectsTicketImageUrlOver2048BeforeProviderConfirmation() {
        Long userId = 10L;
        TossPaymentDTO.ClientConfirmRequest request = preflightRequest("유효한 신청 메시지입니다.", "x".repeat(2049));
        stubFreshPreparedIntent(request, userId);

        assertThatThrownBy(() -> paymentController.confirmTossPayment(request, userId))
                .isInstanceOfSatisfying(BadRequestBusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("INVALID_TICKET_IMAGE_URL"));

        verifyNoPaymentConfirmationSideEffects();
    }

    private TossPaymentDTO.ClientConfirmRequest preflightRequest(String message, String ticketImageUrl) {
        return TossPaymentDTO.ClientConfirmRequest.builder()
                .paymentKey("payment_key")
                .orderId("MATE-1-10-preflight")
                .intentId(10L)
                .partyId(1L)
                .paymentType(PartyApplication.PaymentType.DEPOSIT)
                .message(message)
                .ticketImageUrl(ticketImageUrl)
                .build();
    }

    private PaymentIntent stubAmbiguousConfirmRequestedIntent(
            TossPaymentDTO.ClientConfirmRequest request,
            Long userId) {
        PaymentIntent intent = PaymentIntent.builder()
                .id(request.getIntentId())
                .orderId(request.getOrderId())
                .partyId(request.getPartyId())
                .applicantId(userId)
                .expectedAmount(35000)
                .status(PaymentIntent.IntentStatus.PREPARED)
                .paymentKey(request.getPaymentKey())
                .failureCode("CONFIRM_REQUESTED")
                .paymentType(PartyApplication.PaymentType.DEPOSIT)
                .currency("KRW")
                .build();
        given(paymentIntentService.reserveConfirmationAttempt(
                intent.getId(), userId, request.getPaymentKey())).willReturn(true);
        given(paymentIntentService.findExistingApplicationResponse(request.getOrderId(), userId))
                .willReturn(Optional.empty());
        given(paymentIntentService.resolveIntentForConfirm(request, userId)).willReturn(intent);
        stubIntentLocks(intent);
        return intent;
    }

    private void stubFreshPreparedIntent(TossPaymentDTO.ClientConfirmRequest request, Long userId) {
        PaymentIntent intent = PaymentIntent.builder()
                .id(10L)
                .orderId(request.getOrderId())
                .partyId(request.getPartyId())
                .applicantId(userId)
                .expectedAmount(35000)
                .status(PaymentIntent.IntentStatus.PREPARED)
                .paymentType(PartyApplication.PaymentType.DEPOSIT)
                .currency("KRW")
                .build();

        given(paymentIntentService.findExistingApplicationResponse(request.getOrderId(), userId))
                .willReturn(Optional.empty());
        given(paymentIntentService.resolveIntentForConfirm(request, userId)).willReturn(intent);
        stubIntentLocks(intent);
    }

    private void verifyNoPaymentConfirmationSideEffects() {
        verify(paymentIntentService, never())
                .reserveConfirmationAttempt(any(Long.class), any(Long.class), any(String.class));
        verify(tossPaymentService, never()).confirmPayment(any(), any(), anyInt());
        verify(paymentIntentService, never()).markConfirmed(any(PaymentIntent.class), any(String.class));
        verify(applicationService, never()).createOrGetApplicationWithPayment(
                any(PartyApplicationDTO.Request.class), any(Long.class), any(), any(), anyInt(), any());
        verify(paymentIntentService, never())
                .compensateAfterApplicationFailure(any(Long.class), any(RuntimeException.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"application", "intent", "transaction"})
    void confirmTossPayment_rejectsRetryWhenAnyStoredPaymentKeyConflicts(String mismatchedRecord) {
        Long userId = 10L;
        String orderId = "MATE-1-10-1708500000000";
        TossPaymentDTO.ClientConfirmRequest request = TossPaymentDTO.ClientConfirmRequest.builder()
                .paymentKey("pk_expected")
                .orderId(orderId)
                .partyId(1L)
                .build();

        stubExistingRetry(orderId, userId,
                mismatchedRecord.equals("application") ? "pk_other" : "pk_expected",
                mismatchedRecord.equals("intent") ? "pk_other" : "pk_expected",
                mismatchedRecord.equals("transaction") ? "pk_other" : "pk_expected");

        assertThatThrownBy(() -> paymentController.confirmTossPayment(request, userId))
                .isInstanceOfSatisfying(TossPaymentException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception).extracting("code")
                            .isEqualTo("EXISTING_APPLICATION_PAYMENT_KEY_MISMATCH");
                });

        verify(tossPaymentService, never()).confirmPayment(any(), any(), anyInt());
        verify(applicationService, never()).createOrGetApplicationWithPayment(
                any(PartyApplicationDTO.Request.class), any(Long.class), any(), any(), anyInt(), any());
        verify(paymentIntentService, never()).markApplicationCreated(any(PaymentIntent.class));
        verify(paymentTransactionService, never()).enrichResponse(any(PartyApplicationDTO.Response.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"request", "application", "intent", "transaction"})
    void confirmTossPayment_rejectsRetryWhenAnyPaymentKeyIsMissing(String missingRecord) {
        Long userId = 10L;
        String orderId = "MATE-1-10-1708500000000";
        TossPaymentDTO.ClientConfirmRequest request = TossPaymentDTO.ClientConfirmRequest.builder()
                .paymentKey(missingRecord.equals("request") ? " " : "pk_expected")
                .orderId(orderId)
                .partyId(1L)
                .build();

        stubExistingRetry(orderId, userId,
                missingRecord.equals("application") ? null : "pk_expected",
                missingRecord.equals("intent") ? null : "pk_expected",
                missingRecord.equals("transaction") ? null : "pk_expected");

        assertThatThrownBy(() -> paymentController.confirmTossPayment(request, userId))
                .isInstanceOfSatisfying(TossPaymentException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception).extracting("code")
                            .isEqualTo("EXISTING_APPLICATION_PAYMENT_KEY_MISMATCH");
                });

        verify(tossPaymentService, never()).confirmPayment(any(), any(), anyInt());
        verify(applicationService, never()).createOrGetApplicationWithPayment(
                any(PartyApplicationDTO.Request.class), any(Long.class), any(), any(), anyInt(), any());
        verify(paymentIntentService, never()).markApplicationCreated(any(PaymentIntent.class));
        verify(paymentTransactionService, never()).enrichResponse(any(PartyApplicationDTO.Response.class));
    }

    private PartyApplicationDTO.Response stubExistingRetry(
            String orderId,
            Long userId,
            String applicationPaymentKey,
            String intentPaymentKey,
            String transactionPaymentKey) {
        PartyApplicationDTO.Response existing = PartyApplicationDTO.Response.builder()
                .id(100L)
                .partyId(1L)
                .paymentType(PartyApplication.PaymentType.DEPOSIT)
                .build();
        given(paymentIntentService.findExistingApplicationResponse(orderId, userId))
                .willReturn(Optional.of(existing));
        lenient().when(paymentIntentService.findIntentByOrderId(orderId))
                .thenReturn(Optional.of(PaymentIntent.builder()
                        .id(10L)
                        .orderId(orderId)
                        .partyId(1L)
                        .applicantId(userId)
                        .paymentKey(intentPaymentKey)
                        .status(PaymentIntent.IntentStatus.APPLICATION_CREATED)
                        .build()));
        PaymentIntent existingIntent = paymentIntentService.findIntentByOrderId(orderId).orElseThrow();
        stubIntentLocks(existingIntent);
        given(paymentTransactionService.findByOrderId(orderId))
                .willReturn(Optional.of(PaymentTransaction.builder()
                        .orderId(orderId)
                        .paymentKey(transactionPaymentKey)
                        .paymentStatus(PaymentStatus.PAID)
                        .build()));
        lenient().when(applicationService.getApplicationEntity(100L))
                .thenReturn(PartyApplication.builder()
                        .id(100L)
                        .orderId(orderId)
                        .paymentKey(applicationPaymentKey)
                        .build());
        return existing;
    }

    @Test
    void confirmTossPayment_rejectsRetryWhenExistingTransactionFlowMismatched() {
        Long userId = 10L;

        TossPaymentDTO.ClientConfirmRequest request = TossPaymentDTO.ClientConfirmRequest.builder()
                .paymentKey("pk_test")
                .orderId("MATE-1-10-1708500000000")
                .partyId(1L)
                .flowType(PaymentFlowType.DEPOSIT)
                .build();

        PartyApplicationDTO.Response existing = PartyApplicationDTO.Response.builder()
                .id(100L)
                .partyId(1L)
                .paymentType(PartyApplication.PaymentType.DEPOSIT)
                .build();

        given(paymentIntentService.findExistingApplicationResponse("MATE-1-10-1708500000000", 10L))
                .willReturn(Optional.of(existing));
        given(paymentTransactionService.findByOrderId("MATE-1-10-1708500000000"))
                .willReturn(Optional.of(PaymentTransaction.builder()
                        .id(10L)
                        .orderId("MATE-1-10-1708500000000")
                        .paymentKey("pk_test")
                        .flowType(PaymentFlowType.SELLING_FULL)
                        .build()));

        assertThatThrownBy(() -> paymentController.confirmTossPayment(request, userId))
                .isInstanceOf(TossPaymentException.class)
                .hasMessageContaining("기존 결제 트랜잭션")
                .extracting("code")
                .isEqualTo("PAYMENT_TRANSACTION_FLOW_MISMATCH");
    }

    @Test
    void confirmTossPayment_applicationSaveFails_triggersCompensation() {
        Long userId = 10L;

        TossPaymentDTO.ClientConfirmRequest request = TossPaymentDTO.ClientConfirmRequest.builder()
                .paymentKey("payment_key")
                .orderId("MATE-1-10-1708500000000")
                .partyId(1L)
                .intentId(10L)
                .message("함께 즐거운 관람 부탁드립니다!")
                .build();

        PaymentIntent intent = PaymentIntent.builder()
                .id(10L)
                .orderId("MATE-1-10-1708500000000")
                .partyId(1L)
                .applicantId(10L)
                .expectedAmount(35000)
                .status(PaymentIntent.IntentStatus.PREPARED)
                .paymentType(PartyApplication.PaymentType.DEPOSIT)
                .currency("KRW")
                .build();

        TossPaymentDTO.ConfirmResponse tossResponse = new TossPaymentDTO.ConfirmResponse(
                "payment_key", "MATE-1-10-1708500000000", "DONE", 35000, "카드");

        given(paymentIntentService.findExistingApplicationResponse("MATE-1-10-1708500000000", 10L))
                .willReturn(Optional.empty());
        given(paymentIntentService.resolveIntentForConfirm(request, userId)).willReturn(intent);
        stubIntentLocks(intent);
        given(tossPaymentService.confirmPayment("payment_key", "MATE-1-10-1708500000000", 35000)).willReturn(tossResponse);
        given(applicationService.createOrGetApplicationWithPayment(
                any(PartyApplicationDTO.Request.class),
                eq(userId),
                eq("payment_key"),
                eq("MATE-1-10-1708500000000"),
                eq(35000),
                eq(PartyApplication.PaymentType.DEPOSIT)))
                .willThrow(new IllegalStateException("save failed"));

        assertThatThrownBy(() -> paymentController.confirmTossPayment(request, userId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("save failed");

        verify(paymentIntentService).compensateAfterApplicationFailure(eq(intent.getId()), any(IllegalStateException.class));
    }

    @Test
    void confirmTossPayment_resumedConfirmedIntentRoutesClosedPartyFailureToCompensation() {
        Long userId = 10L;
        String orderId = "MATE-1-10-confirmed-resume";
        TossPaymentDTO.ClientConfirmRequest request = TossPaymentDTO.ClientConfirmRequest.builder()
                .paymentKey("already-confirmed-key")
                .orderId(orderId)
                .partyId(1L)
                .intentId(10L)
                .flowType(PaymentFlowType.DEPOSIT)
                .paymentType(PartyApplication.PaymentType.DEPOSIT)
                .message("confirmed payment resume")
                .build();
        PaymentIntent intent = PaymentIntent.builder()
                .id(10L)
                .orderId(orderId)
                .partyId(1L)
                .applicantId(userId)
                .expectedAmount(35000)
                .paymentKey("already-confirmed-key")
                .status(PaymentIntent.IntentStatus.CONFIRMED)
                .flowType(PaymentFlowType.DEPOSIT)
                .paymentType(PartyApplication.PaymentType.DEPOSIT)
                .currency("KRW")
                .build();

        given(paymentIntentService.findExistingApplicationResponse(orderId, userId)).willReturn(Optional.empty());
        given(paymentIntentService.resolveIntentForConfirm(request, userId)).willReturn(intent);
        stubIntentLocks(intent);
        given(applicationService.createOrGetApplicationWithPayment(
                any(PartyApplicationDTO.Request.class),
                eq(userId),
                eq("already-confirmed-key"),
                eq(orderId),
                eq(35000),
                eq(PartyApplication.PaymentType.DEPOSIT)))
                .willThrow(new IllegalStateException("party closed after provider confirmation"));

        assertThatThrownBy(() -> paymentController.confirmTossPayment(request, userId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("party closed");

        verify(tossPaymentService, never()).confirmPayment(any(), any(), any());
        verify(paymentIntentService).compensateAfterApplicationFailure(
                eq(intent.getId()), any(IllegalStateException.class));
    }

    @SuppressWarnings("unchecked")
    private void stubIntentLocks(PaymentIntent intent) {
        lenient().when(paymentIntentService.withApplicantIntentLocked(
                        eq(intent.getId()), any(Long.class), any(Function.class)))
                .thenAnswer(invocation -> invocation.<Function<PaymentIntent, Object>>getArgument(2).apply(intent));
        lenient().when(paymentIntentService.withConfirmedIntentForApplicationCreation(
                        eq(intent.getId()), any(Long.class), any(String.class), any(Function.class)))
                .thenAnswer(invocation -> invocation.<Function<PaymentIntent, Object>>getArgument(3).apply(intent));
        lenient().doAnswer(invocation -> {
            intent.setStatus(PaymentIntent.IntentStatus.CONFIRMED);
            intent.setPaymentKey(invocation.getArgument(1));
            return null;
        }).when(paymentIntentService).markConfirmed(any(PaymentIntent.class), any(String.class));
    }

    @Test
    void prepareTossPayment_whenTossProviderIsUnavailable_throws503() {
        given(matePaymentModeService.isTossPaymentEnabled()).willReturn(false);

        TossPaymentDTO.PrepareClientRequest request = TossPaymentDTO.PrepareClientRequest.builder()
                .partyId(1L)
                .build();

        assertThatThrownBy(() -> paymentController.prepareTossPayment(request, 10L))
                .isInstanceOf(TossPaymentException.class)
                .hasMessageContaining("Toss 결제")
                .extracting("code")
                .isEqualTo("TOSS_PAYMENT_DISABLED");
    }

    @Test
    void getPaymentCapability_returnsServerOwnedModeAndGates() {
        given(matePaymentModeService.paymentMode()).willReturn("DIRECT_TRADE");
        given(matePaymentModeService.businessMode()).willReturn("DIRECT_TRADE");
        given(matePaymentModeService.paymentProvider()).willReturn("TOSS");
        given(matePaymentModeService.paymentEnvironment()).willReturn("NONE");
        given(matePaymentModeService.isTossPaymentEnabled()).willReturn(false);
        given(matePaymentModeService.isSellingPaymentRequired()).willReturn(false);
        given(matePaymentModeService.isPayoutEnabled()).willReturn(false);
        given(matePaymentModeService.payoutProvider()).willReturn("SIM");

        var response = paymentController.getPaymentCapability();

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().businessMode()).isEqualTo("DIRECT_TRADE");
        assertThat(response.getBody().environment()).isEqualTo("NONE");
        assertThat(response.getBody().tossPaymentEnabled()).isFalse();
        assertThat(response.getBody().sellingPaymentRequired()).isFalse();
    }
}
