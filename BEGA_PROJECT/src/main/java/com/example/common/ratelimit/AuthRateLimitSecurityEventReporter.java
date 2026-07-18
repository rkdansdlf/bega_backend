package com.example.common.ratelimit;

@FunctionalInterface
public interface AuthRateLimitSecurityEventReporter {

    void recordRejected();
}
