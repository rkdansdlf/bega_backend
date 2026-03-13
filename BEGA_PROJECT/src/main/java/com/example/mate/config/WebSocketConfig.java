package com.example.mate.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.lang.NonNull;

import java.util.Arrays;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

        private final AuthenticatedHandshakeInterceptor authenticatedHandshakeInterceptor;
        private final AuthenticatedPrincipalHandshakeHandler authenticatedPrincipalHandshakeHandler;
        private final WebSocketAuthorizationChannelInterceptor webSocketAuthorizationChannelInterceptor;

        @org.springframework.beans.factory.annotation.Value("${app.allowed-origins:http://localhost:3000,http://localhost:5173,http://localhost:5176,http://localhost:8080,http://127.0.0.1:3000,http://127.0.0.1:5173,http://127.0.0.1:5176,http://host.docker.internal:3000,http://host.docker.internal:5173,http://host.docker.internal:5176,http://host.docker.internal:8080}")
        private String allowedOriginsStr;

    @Override
    public void configureMessageBroker(@NonNull MessageBrokerRegistry config) {
        // 클라이언트로 메시지를 보낼 때 prefix
        config.enableSimpleBroker("/topic", "/queue");
        // 클라이언트에서 메시지를 보낼 때 prefix
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(@NonNull ChannelRegistration registration) {
        registration.interceptors(webSocketAuthorizationChannelInterceptor);
    }

    @Override
    public void registerStompEndpoints(@NonNull StompEndpointRegistry registry) {
        // WebSocket 연결 엔드포인트
        String[] allowedOrigins = Arrays.stream(allowedOriginsStr == null ? new String[0] : allowedOriginsStr.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toArray(String[]::new);
        registry.addEndpoint("/ws")
                .addInterceptors(authenticatedHandshakeInterceptor)
                .setAllowedOriginPatterns(allowedOrigins)
                .setHandshakeHandler(authenticatedPrincipalHandshakeHandler);

    }
}
