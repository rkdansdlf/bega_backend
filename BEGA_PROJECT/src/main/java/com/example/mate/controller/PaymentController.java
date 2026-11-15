package com.example.mate.controller;

import com.example.common.dto.ApiResponse;
import com.example.common.web.AuthenticatedUserIds;
import com.example.mate.dto.PartyApplicationDTO;
import com.example.mate.dto.MatePaymentCapabilityDTO;
import com.example.mate.dto.TossPaymentDTO;
import com.example.mate.entity.PaymentIntent;
import com.example.mate.entity.PartyApplication;
import com.example.mate.entity.PaymentFlowType;
import com.example.mate.entity.PaymentStatus;
import com.example.mate.entity.PaymentTransaction;
import com.example.mate.exception.TossPaymentException;
import com.example.mate.service.PartyApplicationService;
import com.example.mate.service.PaymentApplicationInputPreflight;
import com.example.mate.service.PaymentMetricsService;
import com.example.mate.service.PaymentIntentService;
import com.example.mate.service.MatePaymentModeService;
import com.example.mate.service.PaymentTransactionService;
import com.example.mate.service.TossPaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;

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
    private final MatePaymentModeService matePaymentModeService;

    @Operation(summary = "Get Mate payment capability")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "OK",
            content = @Content(schema = @Schema(implementation = MatePaymentCapabilityDTO.Response.class)))
    @GetMapping("/capability")
    public ResponseEntity<MatePaymentCapabilityDTO.Response> getPaymentCapability() {
        return ResponseEntity.ok(new MatePaymentCapabilityDTO.Response(
                matePaymentModeService.paymentMode(),
                matePaymentModeService.businessMode(),
                matePaymentModeService.paymentProvider(),
                matePaymentModeService.paymentEnvironment(),
                matePaymentModeService.isTossPaymentEnabled(),
                matePaymentModeService.isSellingPaymentRequired(),
                matePaymentModeService.isPayoutEnabled(),
                matePaymentModeService.payoutProvider()));
    }

    /**
     * POST /api/payments/toss/prepare
     * 서버가 주문 의도(intent)와 확정 금액을 생성합니다.
     */
    @Operation(summary = "Prepare Mate Toss payment intent")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "OK",
            content = @Content(schema = @Schema(implementation = TossPaymentDTO.PrepareResponse.class)))
    @PostMapping("/toss/prepare")
    public ResponseEntity<TossPaymentDTO.PrepareResponse> prepareTossPayment(
            @RequestBody TossPaymentDTO.PrepareClientRequest request,
            @AuthenticationPrincipal Long userId) {
        ensureTossPaymentEnabled();

        TossPaymentDTO.PrepareResponse response = paymentIntentService.prepareIntent(request, AuthenticatedUserIds.require(userId));
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/payments/toss/confirm
     * 1) intent(또는 legacy strict) 조회
     * 2) 서버 계산 금액으로 Toss 승인
     * 3) 결제 승인된 PartyApplication 생성
     */
    @Operation(summary = "Confirm Mate Toss payment")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "OK",
                    content = @Content(schema = @Schema(implementation = PartyApplicationDTO.Response.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "Created",
                    content = @Content(schema = @Schema(implementation = PartyApplicationDTO.Response.class)))
    })
    @PostMapping("/toss/confirm")
    public ResponseEntity<PartyApplicationDTO.Response> confirmTossPayment(
            @RequestBody TossPaymentDTO.ClientConfirmRequest request,
            @AuthenticationPrincipal Long userId) {
        ensureTossPaymentEnabled();

        log.info("[Payment] Toss 결제 승인 요청: orderId={}, partyId={}, intentId={}",
                request.getOrderId(), request.getPartyId(), request.getIntentId());

        Long applicantId = AuthenticatedUserIds.require(userId);
        PartyApplicationDTO.Response existingByOrderId = resolveExistingResponseOrThrow(request, applicantId);
        if (existingByOrderId != null) {
            PaymentIntent existingIntent = paymentIntentService.findIntentByOrderId(request.getOrderId()).orElse(null);
            if (existingIntent != null) {
                return paymentIntentService.withApplicantIntentLocked(
                        existingIntent.getId(),
                        applicantId,
                        lockedIntent -> {
                            if (isReconciliationHeld(lockedIntent)) {
                                throw reconciliationRequiredConflict();
                            }
                            if (isClosedIntent(lockedIntent)) {
                                throw paymentConflict(
                                        "PAYMENT_INTENT_ALREADY_CLOSED",
                                        "이미 종료된 결제 신청은 재사용할 수 없습니다.");
                            }
                            PartyApplicationDTO.Response currentApplication = resolveExistingResponseOrThrow(
                                    lockedIntent.getOrderId(), applicantId, request);
                            if (currentApplication == null) {
                                throw paymentConflict(
                                        "PAYMENT_APPLICATION_ALREADY_CLOSED",
                                        "이미 종료된 신청은 다시 생성할 수 없습니다.");
                            }
                            validateRequestCompatibilityWithExisting(currentApplication, request, lockedIntent);
                            validateExistingPaymentKeyCompatibility(currentApplication, request, lockedIntent);
                            paymentTransactionService.enrichResponse(currentApplication);
                            paymentMetricsService.recordConfirm("retry");
                            return ResponseEntity.ok(currentApplication);
                        });
            }
            validateRequestCompatibilityWithExisting(existingByOrderId, request, null);
            validateExistingPaymentKeyCompatibility(existingByOrderId, request, null);
            paymentTransactionService.enrichResponse(existingByOrderId);
            paymentMetricsService.recordConfirm("retry");
            return ResponseEntity.ok(existingByOrderId);
        }

        PaymentIntent intent = paymentIntentService.resolveIntentForConfirm(request, applicantId);

        return confirmResolvedIntent(request, applicantId, intent);
    }

    private ResponseEntity<PartyApplicationDTO.Response> confirmResolvedIntent(
            TossPaymentDTO.ClientConfirmRequest request,
            Long applicantId,
            PaymentIntent intent) {
        validateIntentAndRequestCompatibility(intent, request);

        PartyApplicationDTO.Response existingForIntent =
                resolveExistingResponseOrThrow(intent.getOrderId(), applicantId, request);
        if (existingForIntent != null) {
            return paymentIntentService.withApplicantIntentLocked(
                    intent.getId(),
                    applicantId,
                    lockedIntent -> {
                        if (isReconciliationHeld(lockedIntent)) {
                            throw reconciliationRequiredConflict();
                        }
                        if (isClosedIntent(lockedIntent)) {
                            throw paymentConflict(
                                    "PAYMENT_INTENT_ALREADY_CLOSED",
                                    "이미 종료된 결제 신청은 재사용할 수 없습니다.");
                        }
                        PartyApplicationDTO.Response currentApplication = resolveExistingResponseOrThrow(
                                lockedIntent.getOrderId(), applicantId, request);
                        if (currentApplication == null) {
                            throw paymentConflict(
                                    "PAYMENT_APPLICATION_ALREADY_CLOSED",
                                    "이미 종료된 신청은 다시 생성할 수 없습니다.");
                        }
                        validateRequestCompatibilityWithExisting(currentApplication, request, lockedIntent);
                        validateExistingPaymentKeyCompatibility(currentApplication, request, lockedIntent);
                        paymentIntentService.markApplicationCreated(lockedIntent);
                        paymentTransactionService.enrichResponse(currentApplication);
                        paymentMetricsService.recordConfirm("retry");
                        return ResponseEntity.ok(currentApplication);
                    });
        }

        PartyApplication.PaymentType requestedPaymentType = resolveRequestedPaymentType(request);
        if (requestedPaymentType != null && intent.getPaymentType() != null
                && requestedPaymentType != intent.getPaymentType()) {
            throw paymentConflict("PAYMENT_TYPE_SERVER_MISMATCH", "요청한 결제 타입이 서버 계산값과 일치하지 않습니다.");
        }

        if (intent.getStatus() == PaymentIntent.IntentStatus.PREPARED
                || intent.getStatus() == PaymentIntent.IntentStatus.CONFIRMED
                || (intent.getStatus() == PaymentIntent.IntentStatus.RECONCILIATION_REQUIRED
                        && paymentIntentService.hasVerifiedIdentityPaymentBinding(intent.getId()))) {
            request.setMessage(PaymentApplicationInputPreflight.validateAndResolveMessage(
                    request.getMessage(), request.getTicketImageUrl()));
        }

        ConfirmationAttempt confirmationAttempt = paymentIntentService.withApplicantConfirmationSerialized(
                applicantId,
                () -> {
                    boolean ambiguousRetry = paymentIntentService.reserveConfirmationAttempt(
                            intent.getId(), applicantId, request.getPaymentKey());
                    return paymentIntentService.withApplicantIntentLocked(
                            intent.getId(),
                            applicantId,
                            lockedIntent -> confirmLockedIntent(
                                    lockedIntent, request, applicantId, ambiguousRetry));
                });

        if (confirmationAttempt.identityMismatch()) {
            throw paymentConflict(
                    "TOSS_CONFIRM_IDENTITY_MISMATCH_RECONCILIATION_REQUIRED",
                    "결제 승인 응답의 주문 정보가 일치하지 않아 확인이 필요합니다. 고객센터에 문의해주세요.");
        }
        if (confirmationAttempt.reconciliationRequired()) {
            throw reconciliationRequiredConflict();
        }
        TossPaymentDTO.ConfirmResponse tossResponse = confirmationAttempt.response();

        try {
            PartyApplicationService.PaymentCreationResult applicationResult =
                    paymentIntentService.withConfirmedIntentForApplicationCreation(
                            intent.getId(),
                            applicantId,
                            tossResponse.getPaymentKey(),
                            lockedIntent -> {
                                PartyApplicationDTO.Request appRequest = PartyApplicationDTO.Request.builder()
                                        .partyId(lockedIntent.getPartyId())
                                        .message(request.getMessage())
                                        .depositAmount(lockedIntent.getExpectedAmount())
                                        .paymentType(lockedIntent.getPaymentType())
                                        .verificationToken(request.getVerificationToken())
                                        .ticketVerified(request.getTicketVerified())
                                        .ticketImageUrl(request.getTicketImageUrl())
                                        .build();
                                PartyApplicationService.PaymentCreationResult created = applicationService
                                        .createOrGetApplicationWithPayment(
                                                appRequest,
                                                applicantId,
                                                tossResponse.getPaymentKey(),
                                                tossResponse.getOrderId(),
                                                lockedIntent.getExpectedAmount(),
                                                lockedIntent.getPaymentType());
                                PartyApplication applicationEntity = applicationService
                                        .getApplicationEntity(created.response().getId());
                                paymentTransactionService.createOrGetOnConfirm(
                                        applicationEntity,
                                        lockedIntent,
                                        tossResponse.getPaymentKey());
                                if (Boolean.TRUE.equals(applicationEntity.getIsApproved())) {
                                    paymentTransactionService.requestSettlementOnApproval(applicationEntity);
                                }
                                paymentTransactionService.enrichResponse(created.response());
                                return created;
                            });
            PartyApplicationDTO.Response application = applicationResult.response();
            paymentMetricsService.recordConfirm("success");
            log.info("[Payment] 파티 신청 완료: applicationId={}, orderId={}", application.getId(), intent.getOrderId());
            HttpStatus responseStatus = applicationResult.created() ? HttpStatus.CREATED : HttpStatus.OK;
            return ResponseEntity.status(responseStatus).body(application);
        } catch (RuntimeException e) {
            paymentIntentService.compensateAfterApplicationFailure(intent.getId(), e);
            paymentMetricsService.recordConfirm("fail");
            throw e;
        }
    }

    private ConfirmationAttempt confirmLockedIntent(
            PaymentIntent lockedIntent,
            TossPaymentDTO.ClientConfirmRequest request,
            Long applicantId,
            boolean ambiguousRetry) {
        if (isReconciliationHeld(lockedIntent)) {
            throw reconciliationRequiredConflict();
        }
        validateIntentAndRequestCompatibility(lockedIntent, request);
        if (lockedIntent.getStatus() == PaymentIntent.IntentStatus.CONFIRMED
                || lockedIntent.getStatus() == PaymentIntent.IntentStatus.APPLICATION_CREATED
                || paymentIntentService.hasVerifiedIdentityPaymentBinding(lockedIntent.getId())) {
            if (lockedIntent.getPaymentKey() == null
                    || lockedIntent.getPaymentKey().isBlank()
                    || (!lockedIntent.getPaymentKey().equals(request.getPaymentKey())
                            && !paymentIntentService.isVerifiedIdentityResolutionRetry(
                                    lockedIntent.getId(), request.getPaymentKey()))) {
                throw paymentConflict(
                        "CONFIRMED_PAYMENT_KEY_MISMATCH",
                        "이미 승인된 결제 키와 요청 정보가 일치하지 않습니다.");
            }
            if (lockedIntent.getStatus() == PaymentIntent.IntentStatus.APPLICATION_CREATED) {
                PartyApplicationDTO.Response existingApplication = resolveExistingResponseOrThrow(
                        lockedIntent.getOrderId(), applicantId, request);
                if (existingApplication == null) {
                    throw paymentConflict(
                            "PAYMENT_APPLICATION_ALREADY_CLOSED",
                            "이미 종료된 신청은 다시 생성할 수 없습니다.");
                }
                validateRequestCompatibilityWithExisting(existingApplication, request, lockedIntent);
            }
            return new ConfirmationAttempt(new TossPaymentDTO.ConfirmResponse(
                    lockedIntent.getPaymentKey(),
                    lockedIntent.getOrderId(),
                    "DONE",
                    lockedIntent.getExpectedAmount(),
                    null), false, false);
        }
        if (lockedIntent.getStatus() != PaymentIntent.IntentStatus.PREPARED) {
            throw paymentConflict(
                    "PAYMENT_INTENT_NOT_CONFIRMABLE",
                    "이미 종료되었거나 승인할 수 없는 결제 요청입니다.");
        }

        TossPaymentDTO.ConfirmResponse providerResponse;
        if (ambiguousRetry) {
            try {
                providerResponse = tossPaymentService.getPayment(request.getPaymentKey());
            } catch (RuntimeException lookupFailure) {
                log.warn("[Payment] 모호한 결제 승인 상태 조회 실패: intentId={}, exceptionType={}",
                        lockedIntent.getId(), lookupFailure.getClass().getSimpleName());
                paymentIntentService.quarantineUnverifiedConfirmation(
                        lockedIntent, request.getPaymentKey(), null);
                return new ConfirmationAttempt(null, false, true);
            }
            if (!isExactCompletedProviderPayment(lockedIntent, request.getPaymentKey(), providerResponse)) {
                paymentIntentService.quarantineUnverifiedConfirmation(
                        lockedIntent, request.getPaymentKey(), providerResponse);
                return new ConfirmationAttempt(providerResponse, false, true);
            }
        } else {
            providerResponse = tossPaymentService.confirmPayment(
                    request.getPaymentKey(),
                    lockedIntent.getOrderId(),
                    lockedIntent.getExpectedAmount());
            if (providerResponse == null || providerResponse.getTotalAmount() == null) {
                throw paymentBadGateway("TOSS_CONFIRM_RESPONSE_INVALID", "결제 승인 응답이 올바르지 않습니다.");
            }
            if (!hasText(providerResponse.getPaymentKey())
                    || !hasText(providerResponse.getOrderId())
                    || !request.getPaymentKey().equals(providerResponse.getPaymentKey())
                    || !lockedIntent.getOrderId().equals(providerResponse.getOrderId())) {
                paymentIntentService.quarantineProviderIdentityMismatch(
                        lockedIntent, request.getPaymentKey(), providerResponse);
                return new ConfirmationAttempt(providerResponse, true, false);
            }
            if (!"DONE".equalsIgnoreCase(providerResponse.getStatus())) {
                throw paymentBadRequest(
                        "TOSS_CONFIRM_NOT_DONE",
                        "결제 승인이 완료되지 않았습니다: " + providerResponse.getStatus());
            }
            if (!lockedIntent.getExpectedAmount().equals(providerResponse.getTotalAmount())) {
                throw paymentConflict("TOSS_CONFIRM_AMOUNT_MISMATCH", "결제 금액이 서버 계산값과 일치하지 않습니다.");
            }
        }
        paymentIntentService.markConfirmed(lockedIntent, providerResponse.getPaymentKey());
        return new ConfirmationAttempt(providerResponse, false, false);
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
        validateTransactionCompatibilityWithExisting(orderId, request);
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
            throw paymentConflict("EXISTING_APPLICATION_PAYMENT_TYPE_MISMATCH", "요청한 결제 타입이 기존 신청과 일치하지 않습니다.");
        }

        PaymentFlowType requestedFlowType = resolveRequestedFlowType(request);
        if (requestedFlowType != null
                && requestedFlowType != resolveApplicationFlowType(application)) {
            throw paymentConflict("EXISTING_APPLICATION_FLOW_MISMATCH", "요청한 결제 흐름이 기존 신청과 일치하지 않습니다.");
        }

        if (request.getIntentId() != null && intent != null
                && !request.getIntentId().equals(intent.getId())) {
            throw paymentConflict("EXISTING_PAYMENT_INTENT_MISMATCH", "요청한 결제 의도와 기존 결제가 일치하지 않습니다.");
        }

        if (request.getOrderId() != null && intent != null
                && !request.getOrderId().equals(intent.getOrderId())) {
            throw paymentConflict("EXISTING_PAYMENT_ORDER_MISMATCH", "요청한 주문과 기존 결제 요청이 일치하지 않습니다.");
        }

        if (intent != null && intent.getPartyId() != null
                && request.getPartyId() != null
                && !request.getPartyId().equals(intent.getPartyId())) {
            throw paymentConflict("EXISTING_PAYMENT_PARTY_MISMATCH", "요청한 파티와 기존 결제 요청이 일치하지 않습니다.");
        }

        if (intent != null && intent.getFlowType() != null
                && requestedFlowType != null
                && requestedFlowType != intent.getFlowType()) {
            throw paymentConflict("PAYMENT_INTENT_FLOW_MISMATCH", "요청한 결제 흐름이 결제 요청 정보와 일치하지 않습니다.");
        }

        if (intent != null && intent.getPaymentType() != null
                && requestedPaymentType != null
                && requestedPaymentType != intent.getPaymentType()) {
            throw paymentConflict("PAYMENT_INTENT_TYPE_MISMATCH", "요청한 결제 타입이 결제 요청 정보와 일치하지 않습니다.");
        }

        if (request.getPartyId() != null && application.getPartyId() != null
                && !request.getPartyId().equals(application.getPartyId())) {
            throw paymentConflict("EXISTING_APPLICATION_PARTY_MISMATCH", "요청한 파티와 기존 신청이 일치하지 않습니다.");
        }
    }

    private void validateTransactionCompatibilityWithExisting(
            String orderId,
            TossPaymentDTO.ClientConfirmRequest request) {
        if (orderId == null || orderId.isBlank()) {
            return;
        }

        PaymentTransaction transaction = paymentTransactionService.findByOrderId(orderId)
                .orElseThrow(this::existingPaymentKeyConflict);
        String requestedPaymentKey = request != null ? request.getPaymentKey() : null;
        PaymentIntent intent = paymentIntentService.findIntentByOrderId(orderId).orElse(null);
        boolean verifiedResolutionRetry = intent != null
                && paymentIntentService.isVerifiedIdentityResolutionRetry(intent.getId(), requestedPaymentKey);
        if (!hasText(requestedPaymentKey)
                || !hasText(transaction.getPaymentKey())
                || (!requestedPaymentKey.equals(transaction.getPaymentKey())
                        && !(verifiedResolutionRetry
                                && intent != null
                                && intent.getPaymentKey().equals(transaction.getPaymentKey())))) {
            throw existingPaymentKeyConflict();
        }

        PaymentFlowType requestedFlowType = resolveRequestedFlowType(request);
        if (requestedFlowType != null && transaction.getFlowType() != requestedFlowType) {
            throw paymentConflict("PAYMENT_TRANSACTION_FLOW_MISMATCH", "요청한 결제 흐름이 기존 결제 트랜잭션과 일치하지 않습니다.");
        }
        if (transaction.getPaymentStatus() == PaymentStatus.CANCELED
                || transaction.getPaymentStatus() == PaymentStatus.REFUND_FAILED) {
            throw paymentConflict("PAYMENT_TRANSACTION_ALREADY_CLOSED", "기존 결제가 종료되어 재요청할 수 없습니다.");
        }
    }

    private void validateExistingPaymentKeyCompatibility(
            PartyApplicationDTO.Response application,
            TossPaymentDTO.ClientConfirmRequest request,
            PaymentIntent intent) {
        String requestedPaymentKey = request != null ? request.getPaymentKey() : null;
        PartyApplication existingApplication = application == null || application.getId() == null
                ? null
                : applicationService.getApplicationEntity(application.getId());
        boolean verifiedResolutionRetry = intent != null
                && paymentIntentService.isVerifiedIdentityResolutionRetry(intent.getId(), requestedPaymentKey);

        if (!hasText(requestedPaymentKey)
                || existingApplication == null
                || !hasText(existingApplication.getPaymentKey())
                || (!requestedPaymentKey.equals(existingApplication.getPaymentKey())
                        && !(verifiedResolutionRetry
                                && intent != null
                                && intent.getPaymentKey().equals(existingApplication.getPaymentKey())))
                || intent == null
                || !hasText(intent.getPaymentKey())
                || (!requestedPaymentKey.equals(intent.getPaymentKey()) && !verifiedResolutionRetry)) {
            throw existingPaymentKeyConflict();
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean isClosedIntent(PaymentIntent intent) {
        return intent.getStatus() == PaymentIntent.IntentStatus.CANCELED
                || intent.getStatus() == PaymentIntent.IntentStatus.CANCEL_REQUESTED
                || intent.getStatus() == PaymentIntent.IntentStatus.CANCEL_FAILED
                || intent.getStatus() == PaymentIntent.IntentStatus.EXPIRED;
    }

    private TossPaymentException existingPaymentKeyConflict() {
        return paymentConflict(
                "EXISTING_APPLICATION_PAYMENT_KEY_MISMATCH",
                "요청한 결제 키와 기존 신청의 결제 정보가 일치하지 않습니다.");
    }

    private void validateIntentAndRequestCompatibility(PaymentIntent intent, TossPaymentDTO.ClientConfirmRequest request) {
        if (request.getIntentId() != null && !request.getIntentId().equals(intent.getId())) {
            throw paymentConflict("PREPARED_INTENT_ID_MISMATCH", "요청한 결제 의도와 준비 정보가 일치하지 않습니다.");
        }

        if (request.getOrderId() != null && !request.getOrderId().equals(intent.getOrderId())) {
            throw paymentConflict("PREPARED_ORDER_ID_MISMATCH", "요청한 주문과 준비 정보가 일치하지 않습니다.");
        }

        PaymentFlowType requestFlowType = resolveRequestedFlowType(request);
        if (requestFlowType != null
                && intent.getFlowType() != null
                && requestFlowType != intent.getFlowType()) {
            throw paymentConflict("PREPARED_FLOW_TYPE_MISMATCH", "요청한 결제 흐름이 준비 정보와 일치하지 않습니다.");
        }

        PartyApplication.PaymentType requestPaymentType = resolveRequestedPaymentType(request);
        if (requestPaymentType != null
                && intent.getPaymentType() != null
                && requestPaymentType != intent.getPaymentType()) {
            throw paymentConflict("PREPARED_PAYMENT_TYPE_MISMATCH", "요청한 결제 타입이 준비 정보와 일치하지 않습니다.");
        }

        if (request.getPartyId() != null && !request.getPartyId().equals(intent.getPartyId())) {
            throw paymentConflict("PREPARED_PARTY_MISMATCH", "요청한 파티와 결제 준비 정보가 일치하지 않습니다.");
        }

        if ((intent.getStatus() == PaymentIntent.IntentStatus.CONFIRMED
                        || intent.getStatus() == PaymentIntent.IntentStatus.RECONCILIATION_REQUIRED)
                && intent.getPaymentKey() != null
                && request.getPaymentKey() != null
                && !intent.getPaymentKey().equals(request.getPaymentKey())
                && !paymentIntentService.isVerifiedIdentityResolutionRetry(intent.getId(), request.getPaymentKey())) {
            throw paymentConflict("CONFIRMED_PAYMENT_KEY_MISMATCH", "기존 승인된 결제 요청과 결제 키가 일치하지 않습니다.");
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

    /**
     * POST /api/payments/toss/{intentId}/cancel
     * 결제 Intent를 취소합니다.
     *
     * - PREPARED 상태: Toss API 호출 없이 DB 상태만 CANCELED로 변경
     * - 신청 생성된 Intent: 신청 취소 경로 사용을 요구하며 provider 호출 없이 409 반환
     * - CONFIRMED 상태: Toss 결제 취소 API 호출 후 CANCELED로 변경
     * - CANCELED 상태: 멱등 처리 (200 반환)
     * - CANCEL_REQUESTED / CANCEL_FAILED 상태: 409 반환
     *
     * 응답:
     *   200 OK  - 취소 성공 또는 이미 취소된 경우
     *   403 Forbidden - 본인이 아닌 경우
     *   404 Not Found - Intent를 찾을 수 없는 경우
     *   409 Conflict  - 취소 불가 상태 (진행 중, 실패, 만료)
     *   503 Service Unavailable - 직거래 모드에서 호출
     */
    @Operation(summary = "Cancel Mate Toss payment intent")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OK")
    @PostMapping("/toss/{intentId}/cancel")
    public ResponseEntity<ApiResponse<TossPaymentDTO.CancelIntentResponse>> cancelTossPayment(
            @PathVariable Long intentId,
            @RequestBody(required = false) @Valid TossPaymentDTO.CancelIntentRequest request,
            @AuthenticationPrincipal Long userId) {
        ensureTossPaymentEnabled();

        Long authenticatedUserId = AuthenticatedUserIds.require(userId);
        String reason = request != null ? request.getCancelReason() : null;

        log.info("[Payment] 결제 취소 요청: intentId={}, userId={}", intentId, authenticatedUserId);

        PaymentIntent.IntentStatus resultStatus =
                paymentIntentService.cancelPaymentIntent(intentId, authenticatedUserId, reason);

        TossPaymentDTO.CancelIntentResponse response = TossPaymentDTO.CancelIntentResponse.builder()
                .intentId(intentId)
                .status(resultStatus)
                .build();

        return ResponseEntity.ok(ApiResponse.success(
                "결제가 취소되었습니다.",
                response));
    }

    private void ensureTossPaymentEnabled() {
        if (!matePaymentModeService.isTossPaymentEnabled()) {
            throw paymentServiceUnavailable(
                    "TOSS_PAYMENT_DISABLED",
                    "현재 설정에서는 앱 내 Toss 결제를 지원하지 않습니다.");
        }
    }

    private TossPaymentException paymentBadRequest(String code, String message) {
        return new TossPaymentException(code, message, HttpStatus.BAD_REQUEST);
    }

    private TossPaymentException paymentConflict(String code, String message) {
        return new TossPaymentException(code, message, HttpStatus.CONFLICT);
    }

    private TossPaymentException paymentBadGateway(String code, String message) {
        return new TossPaymentException(code, message, HttpStatus.BAD_GATEWAY);
    }

    private TossPaymentException paymentInternalServer(String code, String message) {
        return new TossPaymentException(code, message, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private TossPaymentException paymentServiceUnavailable(String code, String message) {
        return new TossPaymentException(code, message, HttpStatus.SERVICE_UNAVAILABLE);
    }

    private boolean isReconciliationHeld(PaymentIntent intent) {
        return intent != null
                && intent.getStatus() == PaymentIntent.IntentStatus.RECONCILIATION_REQUIRED
                && !paymentIntentService.hasVerifiedIdentityPaymentBinding(intent.getId());
    }

    private TossPaymentException reconciliationRequiredConflict() {
        return paymentConflict(
                "PAYMENT_INTENT_RECONCILIATION_REQUIRED",
                "결제 승인 응답 대사가 필요합니다. 고객센터에 문의해주세요.");
    }

    private boolean isExactCompletedProviderPayment(
            PaymentIntent intent,
            String requestedPaymentKey,
            TossPaymentDTO.ConfirmResponse response) {
        return response != null
                && intent != null
                && requestedPaymentKey != null
                && hasText(response.getPaymentKey())
                && hasText(response.getOrderId())
                && hasText(response.getStatus())
                && requestedPaymentKey.equals(response.getPaymentKey())
                && intent.getOrderId() != null
                && intent.getOrderId().equals(response.getOrderId())
                && "DONE".equals(response.getStatus())
                && intent.getExpectedAmount() != null
                && intent.getExpectedAmount().equals(response.getTotalAmount());
    }

    private record ConfirmationAttempt(
            TossPaymentDTO.ConfirmResponse response,
            boolean identityMismatch,
            boolean reconciliationRequired) {}
}
