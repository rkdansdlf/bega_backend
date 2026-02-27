package com.example.mate.controller;

import com.example.mate.dto.PartyApplicationDTO;
import com.example.mate.dto.TossPaymentDTO;
import com.example.mate.entity.PartyApplication;
import com.example.mate.entity.PaymentFlowType;
import com.example.mate.entity.PaymentIntent;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.security.Principal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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
        given(matePaymentModeService.isDirectTrade()).willReturn(false);
    }

    @Test
    void confirmTossPayment_usesServerAmountAndForcesDepositType() {
        Principal principal = () -> "test@example.com";

        TossPaymentDTO.ClientConfirmRequest request = TossPaymentDTO.ClientConfirmRequest.builder()
                .paymentKey("pk_test")
                .orderId("MATE-1-10-1708500000000")
                .partyId(1L)
                .intentId(10L)
                .paymentType(PartyApplication.PaymentType.DEPOSIT)
                .message("hello")
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

        given(paymentIntentService.resolveUserId(principal)).willReturn(10L);
        given(paymentIntentService.findExistingApplicationResponse("MATE-1-10-1708500000000", 10L))
                .willReturn(Optional.empty());
        given(paymentIntentService.resolveIntentForConfirm(request, principal)).willReturn(intent);
        given(tossPaymentService.confirmPayment("pk_test", "MATE-1-10-1708500000000", 35000)).willReturn(tossResponse);
        given(applicationService.createOrGetApplicationWithPayment(
                any(PartyApplicationDTO.Request.class),
                eq(principal),
                eq("payment_key"),
                eq("MATE-1-10-1708500000000"),
                eq(35000),
                eq(PartyApplication.PaymentType.DEPOSIT)))
                .willReturn(new PartyApplicationService.PaymentCreationResult(created, true));
        given(applicationService.getApplicationEntity(99L))
                .willReturn(PartyApplication.builder().id(99L).orderId("MATE-1-10-1708500000000").build());
        given(paymentTransactionService.createOrGetOnConfirm(any(PartyApplication.class), eq(intent), eq("payment_key")))
                .willReturn(PaymentTransaction.builder().id(1L).build());

        var response = paymentController.confirmTossPayment(request, principal);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(created);

        ArgumentCaptor<PartyApplicationDTO.Request> appReqCaptor = ArgumentCaptor.forClass(PartyApplicationDTO.Request.class);
        verify(applicationService).createOrGetApplicationWithPayment(
                appReqCaptor.capture(),
                eq(principal),
                eq("payment_key"),
                eq("MATE-1-10-1708500000000"),
                eq(35000),
                eq(PartyApplication.PaymentType.DEPOSIT));
        verify(paymentTransactionService).createOrGetOnConfirm(any(PartyApplication.class), eq(intent), eq("payment_key"));
        verify(paymentTransactionService).enrichResponse(created);

        PartyApplicationDTO.Request appRequest = appReqCaptor.getValue();
        assertThat(appRequest.getDepositAmount()).isEqualTo(35000);
        assertThat(appRequest.getPaymentType()).isEqualTo(PartyApplication.PaymentType.DEPOSIT);

        verify(tossPaymentService).confirmPayment("pk_test", "MATE-1-10-1708500000000", 35000);
    }

    @Test
    void confirmTossPayment_returnsExistingApplicationForIdempotentRetry() {
        Principal principal = () -> "test@example.com";

        TossPaymentDTO.ClientConfirmRequest request = TossPaymentDTO.ClientConfirmRequest.builder()
                .paymentKey("pk_test")
                .orderId("MATE-1-10-1708500000000")
                .partyId(1L)
                .build();

        PartyApplicationDTO.Response existing = PartyApplicationDTO.Response.builder()
                .id(100L)
                .partyId(1L)
                .build();

        given(paymentIntentService.resolveUserId(principal)).willReturn(10L);
        given(paymentIntentService.findExistingApplicationResponse("MATE-1-10-1708500000000", 10L))
                .willReturn(Optional.of(existing));
        given(paymentTransactionService.findByOrderId("MATE-1-10-1708500000000"))
                .willReturn(Optional.empty());

        var response = paymentController.confirmTossPayment(request, principal);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(existing);

        verify(paymentIntentService, never()).resolveIntentForConfirm(any(), any());
        verify(tossPaymentService, never()).confirmPayment(any(), any(), any());
    }

    @Test
    void confirmTossPayment_rejectsRetryWhenExistingTransactionFlowMismatched() {
        Principal principal = () -> "test@example.com";

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

        given(paymentIntentService.resolveUserId(principal)).willReturn(10L);
        given(paymentIntentService.findExistingApplicationResponse("MATE-1-10-1708500000000", 10L))
                .willReturn(Optional.of(existing));
        given(paymentTransactionService.findByOrderId("MATE-1-10-1708500000000"))
                .willReturn(Optional.of(PaymentTransaction.builder()
                        .id(10L)
                        .orderId("MATE-1-10-1708500000000")
                        .flowType(PaymentFlowType.SELLING_FULL)
                        .build()));

        assertThatThrownBy(() -> paymentController.confirmTossPayment(request, principal))
                .isInstanceOf(TossPaymentException.class)
                .hasMessageContaining("기존 결제 트랜잭션");
    }

    @Test
    void confirmTossPayment_applicationSaveFails_triggersCompensation() {
        Principal principal = () -> "test@example.com";

        TossPaymentDTO.ClientConfirmRequest request = TossPaymentDTO.ClientConfirmRequest.builder()
                .paymentKey("pk_test")
                .orderId("MATE-1-10-1708500000000")
                .partyId(1L)
                .intentId(10L)
                .message("hello")
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

        given(paymentIntentService.resolveUserId(principal)).willReturn(10L);
        given(paymentIntentService.findExistingApplicationResponse("MATE-1-10-1708500000000", 10L))
                .willReturn(Optional.empty());
        given(paymentIntentService.resolveIntentForConfirm(request, principal)).willReturn(intent);
        given(tossPaymentService.confirmPayment("pk_test", "MATE-1-10-1708500000000", 35000)).willReturn(tossResponse);
        given(applicationService.createOrGetApplicationWithPayment(
                any(PartyApplicationDTO.Request.class),
                eq(principal),
                eq("payment_key"),
                eq("MATE-1-10-1708500000000"),
                eq(35000),
                eq(PartyApplication.PaymentType.DEPOSIT)))
                .willThrow(new IllegalStateException("save failed"));

        assertThatThrownBy(() -> paymentController.confirmTossPayment(request, principal))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("save failed");

        verify(paymentIntentService).compensateAfterApplicationFailure(eq(intent.getId()), any(IllegalStateException.class));
    }

    @Test
    void prepareTossPayment_directTradeMode_throws503() {
        given(matePaymentModeService.isDirectTrade()).willReturn(true);

        TossPaymentDTO.PrepareClientRequest request = TossPaymentDTO.PrepareClientRequest.builder()
                .partyId(1L)
                .build();

        assertThatThrownBy(() -> paymentController.prepareTossPayment(request, () -> "test@example.com"))
                .isInstanceOf(TossPaymentException.class)
                .hasMessageContaining("직거래 모드");
    }
}
