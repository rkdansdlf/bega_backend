package com.example.common.clienterror;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.common.clienterror.dto.ClientErrorEventRequest;
import com.example.common.clienterror.dto.ClientErrorFeedbackRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/client-errors")
@Slf4j
public class ClientErrorController {

    private final ClientErrorLoggingService clientErrorLoggingService;

    @PostMapping
    public ResponseEntity<Void> ingestClientError(
            @Valid @RequestBody ClientErrorEventRequest request,
            Authentication authentication) {
        safelyLog(
                () -> clientErrorLoggingService.logClientError(request, authentication),
                "frontend client error",
                request.eventId());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/feedback")
    public ResponseEntity<Void> ingestClientErrorFeedback(
            @Valid @RequestBody ClientErrorFeedbackRequest request,
            Authentication authentication) {
        safelyLog(
                () -> clientErrorLoggingService.logClientFeedback(request, authentication),
                "frontend client feedback",
                request.eventId());
        return ResponseEntity.accepted().build();
    }

    private void safelyLog(Runnable action, String eventType, String eventId) {
        try {
            action.run();
        } catch (RuntimeException e) {
            log.warn("Failed to process {} eventId={}", eventType, eventId, e);
        }
    }
}
