package com.example.mate.config;

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import com.example.common.config.AllowedOriginResolver;
import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

        private final AuthenticatedHandshakeInterceptor authenticatedHandshakeInterceptor;
        private final AuthenticatedPrincipalHandshakeHandler authenticatedPrincipalHandshakeHandler;
        private final WebSocketAuthorizationChannelInterceptor webSocketAuthorizationChannelInterceptor;
        private final AllowedOriginResolver allowedOriginResolver;

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
        List<String> allowedOrigins = allowedOriginResolver.resolve();
        registry.addEndpoint("/ws")
                .addInterceptors(authenticatedHandshakeInterceptor)
                .setAllowedOriginPatterns(allowedOrigins.toArray(new String[0]))
                .setHandshakeHandler(authenticatedPrincipalHandshakeHandler);

    }
}
