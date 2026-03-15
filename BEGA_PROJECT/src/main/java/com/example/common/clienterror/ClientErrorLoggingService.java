package com.example.common.clienterror;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.auth.service.CustomUserDetails;
import com.example.common.clienterror.dto.ClientErrorEventRequest;
import com.example.common.clienterror.dto.ClientErrorFeedbackRequest;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class ClientErrorLoggingService {

    private final MeterRegistry meterRegistry;
    private final ClientErrorEventRepository eventRepository;
    private final ClientErrorFeedbackRepository feedbackRepository;

    @Transactional
    public void logClientError(ClientErrorEventRequest request, Authentication authentication) {
        Long authenticatedUserId = extractUserId(authentication);
        ClientErrorSource source = ClientErrorSource.fromCategory(request.category());
        ClientErrorBucket bucket = source.getBucket();
        String sanitizedMessage = ClientErrorSupport.sanitize(request.message(), ClientErrorSupport.MESSAGE_LOG_LIMIT);
        String sanitizedRoute = ClientErrorSupport.sanitize(request.route(), ClientErrorSupport.ROUTE_LOG_LIMIT);
        String sanitizedEndpoint = ClientErrorSupport.sanitize(request.endpoint(), ClientErrorSupport.ENDPOINT_LOG_LIMIT);
        String normalizedRoute = ClientErrorSupport.normalizeRoute(request.route());
        String normalizedEndpoint = ClientErrorSupport.normalizeEndpoint(request.endpoint());
        String statusGroup = ClientErrorSupport.normalizeStatusGroup(request.statusCode());
        String fingerprint = ClientErrorSupport.buildFingerprint(
                bucket,
                sanitizedMessage,
                normalizedRoute,
                request.statusCode(),
                ClientErrorSupport.sanitize(request.method(), 16),
                normalizedEndpoint);

        recordClientErrorMetric(request);

        if (!eventRepository.existsByEventId(request.eventId())) {
            eventRepository.save(ClientErrorEventEntity.builder()
                    .eventId(ClientErrorSupport.sanitize(request.eventId(), 64))
                    .bucket(bucket)
                    .source(source)
                    .message(sanitizedMessage)
                    .stack(ClientErrorSupport.sanitize(request.stack(), ClientErrorSupport.STACK_LOG_LIMIT))
                    .componentStack(ClientErrorSupport.sanitize(request.componentStack(), ClientErrorSupport.STACK_LOG_LIMIT))
                    .route(sanitizedRoute == null ? "/" : sanitizedRoute)
                    .normalizedRoute(normalizedRoute)
                    .statusCode(request.statusCode())
                    .statusGroup(statusGroup)
                    .responseCode(ClientErrorSupport.sanitize(request.responseCode(), 64))
                    .method(ClientErrorSupport.sanitize(request.method(), 16))
                    .endpoint(sanitizedEndpoint)
                    .normalizedEndpoint(normalizedEndpoint)
                    .occurredAt(ClientErrorSupport.parseOccurredAt(request.timestamp()))
                    .sessionId(ClientErrorSupport.sanitize(request.sessionId(), 128))
                    .userId(authenticatedUserId != null ? authenticatedUserId : request.userId())
                    .fingerprint(fingerprint)
                    .feedbackCount(0)
                    .build());
        }

        log.info(
                "event=frontend_client_error eventId={} category={} route={} statusCode={} responseCode={} method={} endpoint={} userId={} sessionId={} message={}",
                ClientErrorSupport.sanitize(request.eventId(), 64),
                ClientErrorSupport.sanitize(request.category(), 64),
                sanitizedRoute,
                request.statusCode(),
                ClientErrorSupport.sanitize(request.responseCode(), 64),
                ClientErrorSupport.sanitize(request.method(), 16),
                sanitizedEndpoint,
                authenticatedUserId,
                ClientErrorSupport.sanitize(request.sessionId(), 128),
                sanitizedMessage);

        if (request.stack() != null || request.componentStack() != null) {
            log.info(
                    "event=frontend_client_error_stack eventId={} stack={} componentStack={}",
                    ClientErrorSupport.sanitize(request.eventId(), 64),
                    ClientErrorSupport.sanitize(request.stack(), ClientErrorSupport.STACK_LOG_LIMIT),
                    ClientErrorSupport.sanitize(request.componentStack(), ClientErrorSupport.STACK_LOG_LIMIT));
        }
    }

    @Transactional
    public void logClientFeedback(ClientErrorFeedbackRequest request, Authentication authentication) {
        Long authenticatedUserId = extractUserId(authentication);

        recordClientFeedbackMetric(request);
        feedbackRepository.save(ClientErrorFeedbackEntity.builder()
                .eventId(ClientErrorSupport.sanitize(request.eventId(), 64))
                .comment(ClientErrorSupport.sanitize(request.comment(), ClientErrorSupport.COMMENT_LOG_LIMIT))
                .actionTaken(ClientErrorSupport.sanitize(request.actionTaken(), 64))
                .route(ClientErrorSupport.sanitize(request.route(), ClientErrorSupport.ROUTE_LOG_LIMIT))
                .occurredAt(ClientErrorSupport.parseOccurredAt(request.timestamp()))
                .build());
        eventRepository.incrementFeedbackCount(request.eventId());

        log.info(
                "event=frontend_client_feedback eventId={} actionTaken={} route={} userId={} comment={}",
                ClientErrorSupport.sanitize(request.eventId(), 64),
                ClientErrorSupport.sanitize(request.actionTaken(), 64),
                ClientErrorSupport.sanitize(request.route(), ClientErrorSupport.ROUTE_LOG_LIMIT),
                authenticatedUserId,
                ClientErrorSupport.sanitize(request.comment(), ClientErrorSupport.COMMENT_LOG_LIMIT));
    }

    private Long extractUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof CustomUserDetails customUserDetails) {
            return customUserDetails.getId();
        }

        return null;
    }

    private void recordClientErrorMetric(ClientErrorEventRequest request) {
        Counter.builder("frontend_client_errors_total")
                .description("Browser-reported frontend client error events")
                .tag("category", ClientErrorSupport.normalizeTag(request.category(), "unknown"))
                .tag("route", ClientErrorSupport.normalizeRoute(request.route()))
                .tag("status_group", ClientErrorSupport.normalizeStatusGroup(request.statusCode()))
                .register(meterRegistry)
                .increment();
    }

    private void recordClientFeedbackMetric(ClientErrorFeedbackRequest request) {
        Counter.builder("frontend_client_feedback_total")
                .description("Browser-submitted frontend error feedback events")
                .tag("route", ClientErrorSupport.normalizeRoute(request.route()))
                .tag("action_taken", ClientErrorSupport.normalizeTag(request.actionTaken(), "unknown"))
                .register(meterRegistry)
                .increment();
    }
}
