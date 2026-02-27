package com.example.common.image;

import io.micrometer.core.instrument.Counter;
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

    private String normalizeTag(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.trim().toLowerCase();
    }
}
