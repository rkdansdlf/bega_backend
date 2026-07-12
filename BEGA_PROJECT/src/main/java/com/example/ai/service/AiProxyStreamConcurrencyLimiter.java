package com.example.ai.service;

import com.example.ai.exception.AiProxyException;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Locale;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class AiProxyStreamConcurrencyLimiter {

    public static final String STREAMS_BUSY_CODE = "AI_PROXY_STREAMS_BUSY";

    private final Semaphore semaphore;
    private final int maxConcurrentStreams;
    private final MeterRegistry meterRegistry;

    public AiProxyStreamConcurrencyLimiter(
            @Value("${app.ai.proxy.max-concurrent-streams:32}") int maxConcurrentStreams,
            MeterRegistry meterRegistry) {
        this.maxConcurrentStreams = Math.max(1, maxConcurrentStreams);
        this.semaphore = new Semaphore(this.maxConcurrentStreams);
        this.meterRegistry = meterRegistry;
        Gauge.builder("ai_proxy_stream_active", this, AiProxyStreamConcurrencyLimiter::getActiveStreams)
                .description("Active backend AI streaming proxy responses")
                .register(meterRegistry);
        Gauge.builder("ai_proxy_stream_limit", this, AiProxyStreamConcurrencyLimiter::getMaxConcurrentStreams)
                .description("Maximum concurrent backend AI streaming proxy responses")
                .register(meterRegistry);
    }

    public Permit acquire() {
        return acquire("unknown");
    }

    public Permit acquire(String endpoint) {
        if (!semaphore.tryAcquire()) {
            recordEvent(endpoint, "rejected");
            throw new AiProxyException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    STREAMS_BUSY_CODE,
                    "AI 스트리밍 요청이 많습니다. 잠시 후 다시 시도해주세요.");
        }
        recordEvent(endpoint, "accepted");
        return new Permit();
    }

    public int getAvailablePermits() {
        return semaphore.availablePermits();
    }

    public int getActiveStreams() {
        return maxConcurrentStreams - semaphore.availablePermits();
    }

    public int getMaxConcurrentStreams() {
        return maxConcurrentStreams;
    }

    private void recordEvent(String endpoint, String result) {
        meterRegistry.counter(
                        "ai_proxy_stream_events_total",
                        "endpoint", normalizeEndpoint(endpoint),
                        "result", result)
                .increment();
    }

    private String normalizeEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return "unknown";
        }
        String normalized = endpoint.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_\\-]", "_");
        if ("chat_stream".equals(normalized) || "coach_analyze".equals(normalized)) {
            return normalized;
        }
        return "unknown";
    }

    public final class Permit implements AutoCloseable {
        private final AtomicBoolean closed = new AtomicBoolean(false);

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                semaphore.release();
            }
        }
    }
}
