package com.example.mate.controller;

import com.example.mate.dto.PartyApplicationDTO;
import com.example.mate.dto.TossPaymentDTO;
import com.example.mate.entity.PaymentIntent;
import com.example.mate.entity.PartyApplication;
import com.example.mate.entity.PaymentFlowType;
import com.example.mate.exception.TossPaymentException;
import com.example.mate.service.PartyApplicationService;
import com.example.mate.service.PaymentMetricsService;
import com.example.mate.service.PaymentIntentService;
import com.example.mate.service.PaymentTransactionService;
import com.example.mate.service.TossPaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final TossPaymentService tossPaymentService;
    private final PartyApplicationService applicationService;
    private final PaymentIntentService paymentIntentService;
    private final PaymentTransactionService paymentTransactionService;
    private final PaymentMetricsService paymentMetricsService;

    /**
     * POST /api/payments/toss/prepare
     * 서버가 주문 의도(intent)와 확정 금액을 생성합니다.
     */
    @PostMapping("/toss/prepare")
    public ResponseEntity<TossPaymentDTO.PrepareResponse> prepareTossPayment(
            @RequestBody TossPaymentDTO.PrepareClientRequest request,
            Principal principal) {

        TossPaymentDTO.PrepareResponse response = paymentIntentService.prepareIntent(request, principal);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/payments/toss/confirm
     * 1) intent(또는 legacy strict) 조회
     * 2) 서버 계산 금액으로 Toss 승인
     * 3) 결제 승인된 PartyApplication 생성
     */
    @PostMapping("/toss/confirm")
    public ResponseEntity<?> confirmTossPayment(
            @RequestBody TossPaymentDTO.ClientConfirmRequest request,
            Principal principal) {

        log.info("[Payment] Toss 결제 승인 요청: orderId={}, partyId={}, intentId={}",
                request.getOrderId(), request.getPartyId(), request.getIntentId());

        Long applicantId = paymentIntentService.resolveUserId(principal);
        PartyApplicationDTO.Response existingByOrderId = resolveExistingResponseOrThrow(request, applicantId);
        if (existingByOrderId != null) {
            PaymentIntent existingIntent = paymentIntentService.findIntentByOrderId(request.getOrderId()).orElse(null);
            validateRequestCompatibilityWithExisting(existingByOrderId, request, existingIntent);
            paymentTransactionService.enrichResponse(existingByOrderId);
            paymentMetricsService.recordConfirm("retry");
            return ResponseEntity.ok(existingByOrderId);
        }

        PaymentIntent intent = paymentIntentService.resolveIntentForConfirm(request, principal);

        validateIntentAndRequestCompatibility(intent, request);

        existingByOrderId = resolveExistingResponseOrThrow(intent.getOrderId(), applicantId, request);
        if (existingByOrderId != null) {
            validateRequestCompatibilityWithExisting(existingByOrderId, request, intent);
            paymentIntentService.markApplicationCreated(intent);
            paymentTransactionService.enrichResponse(existingByOrderId);
            paymentMetricsService.recordConfirm("retry");
            return ResponseEntity.ok(existingByOrderId);
        }

        PartyApplication.PaymentType requestedPaymentType = resolveRequestedPaymentType(request);
        if (requestedPaymentType != null && intent.getPaymentType() != null
                && requestedPaymentType != intent.getPaymentType()) {
            throw new TossPaymentException("요청한 결제 타입이 서버 계산값과 일치하지 않습니다.", HttpStatus.CONFLICT);
        }

        TossPaymentDTO.ConfirmResponse tossResponse;
        if (intent.getStatus() == PaymentIntent.IntentStatus.CONFIRMED) {
            if (intent.getPaymentKey() == null || intent.getPaymentKey().isBlank()) {
                // CONFIRMED 상태인데 paymentKey가 없는 것은 데이터 불일치 상황
                throw new TossPaymentException("이미 승인된 결제이지만 결제 키 정보를 찾을 수 없습니다. 고객센터에 문의해주세요.", HttpStatus.INTERNAL_SERVER_ERROR);
            }
            tossResponse = new TossPaymentDTO.ConfirmResponse(
                    intent.getPaymentKey(),
                    intent.getOrderId(),
                    "DONE",
                    intent.getExpectedAmount(),
                    null);
        } else {
            tossResponse = tossPaymentService.confirmPayment(
                    request.getPaymentKey(),
                    intent.getOrderId(),
                    intent.getExpectedAmount());

            if (tossResponse == null || tossResponse.getTotalAmount() == null) {
                throw new TossPaymentException("결제 승인 응답이 올바르지 않습니다.", HttpStatus.BAD_GATEWAY);
            }
            if (!"DONE".equalsIgnoreCase(tossResponse.getStatus())) {
                throw new TossPaymentException(
                        "결제 승인이 완료되지 않았습니다: " + tossResponse.getStatus(),
                        HttpStatus.BAD_REQUEST);
            }
            if (!intent.getExpectedAmount().equals(tossResponse.getTotalAmount())) {
                throw new TossPaymentException("결제 금액이 서버 계산값과 일치하지 않습니다.", HttpStatus.CONFLICT);
            }

            paymentIntentService.markConfirmed(intent, tossResponse.getPaymentKey());
        }

        PartyApplicationDTO.Request appRequest = PartyApplicationDTO.Request.builder()
                .partyId(intent.getPartyId())
                .message(request.getMessage())
                .depositAmount(intent.getExpectedAmount())
                .paymentType(intent.getPaymentType())
                .verificationToken(request.getVerificationToken())
                .ticketVerified(request.getTicketVerified())
                .ticketImageUrl(request.getTicketImageUrl())
                .build();

        try {
            PartyApplicationDTO.Response application = applicationService.createApplicationWithPayment(
                appRequest,
                principal,
                tossResponse.getPaymentKey(),
                tossResponse.getOrderId(),
                intent.getExpectedAmount(),
                intent.getPaymentType());

            var applicationEntity = applicationService.getApplicationEntity(application.getId());
            paymentTransactionService.createOrGetOnConfirm(
                    applicationEntity,
                    intent,
                    tossResponse.getPaymentKey());
            if (Boolean.TRUE.equals(applicationEntity.getIsApproved())) {
                paymentTransactionService.requestSettlementOnApproval(applicationEntity);
            }
            paymentTransactionService.enrichResponse(application);

            paymentIntentService.markApplicationCreated(intent);
            paymentMetricsService.recordConfirm("success");
            log.info("[Payment] 파티 신청 완료: applicationId={}, orderId={}", application.getId(), intent.getOrderId());
            return ResponseEntity.status(HttpStatus.CREATED).body(application);
        } catch (RuntimeException e) {
            paymentIntentService.compensateAfterApplicationFailure(intent, e);
            paymentMetricsService.recordConfirm("fail");
            throw e;
        }
    }

    private PartyApplicationDTO.Response resolveExistingResponseOrThrow(
            TossPaymentDTO.ClientConfirmRequest request,
            Long applicantId) {
        return resolveExistingResponseOrThrow(request.getOrderId(), applicantId, request);
    }

    private PartyApplicationDTO.Response resolveExistingResponseOrThrow(
            String orderId,
            Long applicantId,
            TossPaymentDTO.ClientConfirmRequest request) {
        if (orderId == null || orderId.isBlank()) {
            return null;
        }

        Optional<PartyApplicationDTO.Response> existing = paymentIntentService
                .findExistingApplicationResponse(orderId, applicantId);
        if (existing.isEmpty()) {
            return null;
        }

        validateRequestCompatibilityWithExisting(existing.get(), request);
        return existing.get();
    }

    private PartyApplication.PaymentType resolveRequestedPaymentType(TossPaymentDTO.ClientConfirmRequest request) {
        if (request.getPaymentType() != null) {
            return request.getPaymentType();
        }

        PaymentFlowType flowType = request.getFlowType();
        if (flowType == null) {
            return null;
        }

        return flowType == PaymentFlowType.SELLING_FULL
                ? PartyApplication.PaymentType.FULL
                : PartyApplication.PaymentType.DEPOSIT;
    }

    private void validateRequestCompatibilityWithExisting(
            PartyApplicationDTO.Response application,
            TossPaymentDTO.ClientConfirmRequest request) {

        validateRequestCompatibilityWithExisting(application, request, null);
    }

    private void validateRequestCompatibilityWithExisting(
            PartyApplicationDTO.Response application,
            TossPaymentDTO.ClientConfirmRequest request,
            PaymentIntent intent) {
        PartyApplication.PaymentType requestedPaymentType = resolveRequestedPaymentType(request);
        if (requestedPaymentType != null
                && application.getPaymentType() != null
                && requestedPaymentType != application.getPaymentType()) {
            throw new TossPaymentException("요청한 결제 타입이 기존 신청과 일치하지 않습니다.", HttpStatus.CONFLICT);
        }

        PaymentFlowType requestedFlowType = resolveRequestedFlowType(request);
        if (requestedFlowType != null
                && requestedFlowType != resolveApplicationFlowType(application)) {
            throw new TossPaymentException("요청한 결제 흐름이 기존 신청과 일치하지 않습니다.", HttpStatus.CONFLICT);
        }

        if (request.getIntentId() != null && intent != null
                && !request.getIntentId().equals(intent.getId())) {
            throw new TossPaymentException("요청한 결제 의도와 기존 결제가 일치하지 않습니다.", HttpStatus.CONFLICT);
        }

        if (request.getOrderId() != null && intent != null
                && !request.getOrderId().equals(intent.getOrderId())) {
            throw new TossPaymentException("요청한 주문과 기존 결제 요청이 일치하지 않습니다.", HttpStatus.CONFLICT);
        }

        if (intent != null && intent.getPartyId() != null
                && request.getPartyId() != null
                && !request.getPartyId().equals(intent.getPartyId())) {
            throw new TossPaymentException("요청한 파티와 기존 결제 요청이 일치하지 않습니다.", HttpStatus.CONFLICT);
        }

        if (intent != null && intent.getFlowType() != null
                && requestedFlowType != null
                && requestedFlowType != intent.getFlowType()) {
            throw new TossPaymentException("요청한 결제 흐름이 결제 요청 정보와 일치하지 않습니다.", HttpStatus.CONFLICT);
        }

        if (intent != null && intent.getPaymentType() != null
                && requestedPaymentType != null
                && requestedPaymentType != intent.getPaymentType()) {
            throw new TossPaymentException("요청한 결제 타입이 결제 요청 정보와 일치하지 않습니다.", HttpStatus.CONFLICT);
        }

        if (request.getPartyId() != null && application.getPartyId() != null
                && !request.getPartyId().equals(application.getPartyId())) {
            throw new TossPaymentException("요청한 파티와 기존 신청이 일치하지 않습니다.", HttpStatus.CONFLICT);
        }
    }

    private void validateIntentAndRequestCompatibility(PaymentIntent intent, TossPaymentDTO.ClientConfirmRequest request) {
        if (request.getIntentId() != null && !request.getIntentId().equals(intent.getId())) {
            throw new TossPaymentException("요청한 결제 의도와 준비 정보가 일치하지 않습니다.", HttpStatus.CONFLICT);
        }

        if (request.getOrderId() != null && !request.getOrderId().equals(intent.getOrderId())) {
            throw new TossPaymentException("요청한 주문과 준비 정보가 일치하지 않습니다.", HttpStatus.CONFLICT);
        }

        PaymentFlowType requestFlowType = resolveRequestedFlowType(request);
        if (requestFlowType != null
                && intent.getFlowType() != null
                && requestFlowType != intent.getFlowType()) {
            throw new TossPaymentException("요청한 결제 흐름이 준비 정보와 일치하지 않습니다.", HttpStatus.CONFLICT);
        }

        PartyApplication.PaymentType requestPaymentType = resolveRequestedPaymentType(request);
        if (requestPaymentType != null
                && intent.getPaymentType() != null
                && requestPaymentType != intent.getPaymentType()) {
            throw new TossPaymentException("요청한 결제 타입이 준비 정보와 일치하지 않습니다.", HttpStatus.CONFLICT);
        }

        if (request.getPartyId() != null && !request.getPartyId().equals(intent.getPartyId())) {
            throw new TossPaymentException("요청한 파티와 결제 준비 정보가 일치하지 않습니다.", HttpStatus.CONFLICT);
        }

        if (intent.getStatus() == PaymentIntent.IntentStatus.CONFIRMED
                && intent.getPaymentKey() != null
                && request.getPaymentKey() != null
                && !intent.getPaymentKey().equals(request.getPaymentKey())) {
            throw new TossPaymentException("기존 승인된 결제 요청과 결제 키가 일치하지 않습니다.", HttpStatus.CONFLICT);
        }
    }

    private PaymentFlowType resolveRequestedFlowType(TossPaymentDTO.ClientConfirmRequest request) {
        if (request.getFlowType() != null) {
            return request.getFlowType();
        }

        PartyApplication.PaymentType paymentType = request.getPaymentType();
        if (paymentType == null) {
            return null;
        }

        return paymentType == PartyApplication.PaymentType.FULL
                ? PaymentFlowType.SELLING_FULL
                : PaymentFlowType.DEPOSIT;
    }

    private PaymentFlowType resolveApplicationFlowType(PartyApplicationDTO.Response application) {
        if (application == null || application.getPaymentType() == null) {
            return null;
        }

        return application.getPaymentType() == PartyApplication.PaymentType.FULL
                ? PaymentFlowType.SELLING_FULL
                : PaymentFlowType.DEPOSIT;
    }
}
