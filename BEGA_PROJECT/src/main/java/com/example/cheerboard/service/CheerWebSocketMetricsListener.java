package com.example.cheerboard.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

@Component
@RequiredArgsConstructor
public class CheerWebSocketMetricsListener {

    private static final String BATTLE_TOPIC_PREFIX = "/topic/battle/";

    private final CheerMonitoringMetricsService metricsService;

    @EventListener
    public void onSessionConnect(SessionConnectEvent event) {
        metricsService.recordWebSocketEvent("connect");
    }

    @EventListener
    public void onSessionDisconnect(SessionDisconnectEvent event) {
        metricsService.recordWebSocketEvent("disconnect");
    }

    @EventListener
    public void onSessionSubscribe(SessionSubscribeEvent event) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.wrap(event.getMessage());
        String destination = accessor.getDestination();
        if (destination != null && destination.startsWith(BATTLE_TOPIC_PREFIX)) {
            metricsService.recordWebSocketEvent("battle_subscribe");
        }
    }
}
