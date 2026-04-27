package com.example.common.image;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ImageOptimizationMetricsService {

    private final MeterRegistry meterRegistry;

    public void record(String source, String result) {
        Counter.builder("image_optimization_total")
                .description("이미지 최적화 처리 결과 건수")
                .tag("source", normalizeTag(source))
                .tag("result", normalizeTag(result))
                .register(meterRegistry)
                .increment();
    }

    public void recordRequest(String source) {
        Counter.builder("image_upload_requests_total")
                .description("이미지 업로드/분석 요청 건수")
                .tag("source", normalizeTag(source))
                .register(meterRegistry)
                .increment();
    }

    public void recordReject(String source, String reason) {
        Counter.builder("image_upload_rejections_total")
                .description("이미지 업로드/분석 거절 건수")
                .tag("source", normalizeTag(source))
                .tag("reason", normalizeTag(reason))
                .register(meterRegistry)
                .increment();
    }

    public void recordBytes(String source, long originalBytes, long storedBytes) {
        String normalizedSource = normalizeTag(source);
        DistributionSummary.builder("image_upload_original_bytes")
                .description("원본 이미지 바이트 수")
                .baseUnit("bytes")
                .tag("source", normalizedSource)
                .register(meterRegistry)
                .record(Math.max(0L, originalBytes));

        DistributionSummary.builder("image_upload_stored_bytes")
                .description("저장 이미지 바이트 수")
                .baseUnit("bytes")
                .tag("source", normalizedSource)
                .register(meterRegistry)
                .record(Math.max(0L, storedBytes));

        if (originalBytes > 0L) {
            double ratio = (double) storedBytes / (double) originalBytes;
            DistributionSummary.builder("image_upload_compression_ratio")
                    .description("저장 바이트 / 원본 바이트 비율")
                    .tag("source", normalizedSource)
                    .register(meterRegistry)
                    .record(Math.max(0d, ratio));
        }
    }

    public void recordLegacyEndpoint(String endpoint) {
        Counter.builder("media_legacy_endpoint_calls_total")
                .description("레거시 이미지 업로드 엔드포인트 호출 수")
                .tag("endpoint", normalizeTag(endpoint))
                .register(meterRegistry)
                .increment();
    }

    public void recordMediaInit(String domain) {
        Counter.builder("media_upload_init_total")
                .description("공통 미디어 업로드 init 요청 수")
                .tag("domain", normalizeTag(domain))
                .register(meterRegistry)
                .increment();
    }

    public void recordMediaFinalize(String domain, String result) {
        Counter.builder("media_upload_finalize_total")
                .description("공통 미디어 업로드 finalize 결과 수")
                .tag("domain", normalizeTag(domain))
                .tag("result", normalizeTag(result))
                .register(meterRegistry)
                .increment();
    }

    public void recordMediaCleanup(String target, String result) {
        Counter.builder("media_cleanup_total")
                .description("공통 미디어 cleanup 처리 결과 수")
                .tag("target", normalizeTag(target))
                .tag("result", normalizeTag(result))
                .register(meterRegistry)
                .increment();
    }

    private String normalizeTag(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.trim().toLowerCase();
    }
}
