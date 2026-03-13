package com.example.cheerboard.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CheerMonitoringMetricsService {

    private final MeterRegistry meterRegistry;

    public void recordBattleVote(String result) {
        Counter.builder("cheer_battle_vote_total")
                .description("응원 배틀 투표 처리 결과 건수")
                .tag("result", normalizeTag(result))
                .register(meterRegistry)
                .increment();
    }

    public void recordWebSocketEvent(String event) {
        Counter.builder("cheer_websocket_events_total")
                .description("응원 배틀 웹소켓 이벤트 건수")
                .tag("event", normalizeTag(event))
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
