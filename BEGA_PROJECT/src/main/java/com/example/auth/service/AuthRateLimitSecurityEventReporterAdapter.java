package com.example.auth.service;

import com.example.common.ratelimit.AuthRateLimitSecurityEventReporter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthRateLimitSecurityEventReporterAdapter implements AuthRateLimitSecurityEventReporter {

    private final AuthSecurityMonitoringService authSecurityMonitoringService;

    @Override
    public void recordRejected() {
        authSecurityMonitoringService.recordAuthRateLimitReject();
    }
}
