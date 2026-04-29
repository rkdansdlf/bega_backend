package com.example.mate.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * STOMP WebSocket 동시 세션 수와 누적 connect/disconnect를 Micrometer로 노출한다.
 *
 * 노출 메트릭:
 * - websocket.sessions.active (gauge): 현재 활성 세션 수
 * - websocket.sessions.connect.total (counter): 누적 connect 횟수
 * - websocket.sessions.disconnect.total (counter): 누적 disconnect 횟수
 *
 * Prometheus 스크레이프 후 Grafana에서 동시 접속자, connect/disconnect 비율을 시각화한다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketSessionMetrics {

    private final MeterRegistry meterRegistry;
    private final AtomicInteger activeSessions = new AtomicInteger(0);
    private Counter connectCounter;
    private Counter disconnectCounter;

    @PostConstruct
    void init() {
        meterRegistry.gauge("websocket.sessions.active", activeSessions);
        connectCounter = Counter.builder("websocket.sessions.connect.total")
                .description("Total number of STOMP session connect events")
                .register(meterRegistry);
        disconnectCounter = Counter.builder("websocket.sessions.disconnect.total")
                .description("Total number of STOMP session disconnect events")
                .register(meterRegistry);
    }

    @EventListener
    public void onSessionConnected(SessionConnectedEvent event) {
        int now = activeSessions.incrementAndGet();
        connectCounter.increment();
        if (log.isDebugEnabled()) {
            log.debug("STOMP session connected. active={}", now);
        }
    }

    @EventListener
    public void onSessionDisconnected(SessionDisconnectEvent event) {
        int now = activeSessions.updateAndGet(current -> Math.max(0, current - 1));
        disconnectCounter.increment();
        if (log.isDebugEnabled()) {
            log.debug("STOMP session disconnected. active={}", now);
        }
    }

    public int getActiveSessions() {
        return activeSessions.get();
    }
}
