package com.example.mate.config;

import java.security.Principal;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

@Component
public class AuthenticatedHandshakeInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {
        Principal principal = request.getPrincipal();
        if (isAuthenticatedPrincipal(principal)) {
            return true;
        }

        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        return false;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {
        // no-op
    }

    private boolean isAuthenticatedPrincipal(Principal principal) {
        if (principal == null) {
            return false;
        }

        if ("anonymousUser".equals(principal.getName())) {
            return false;
        }

        if (principal instanceof Authentication authentication) {
            return authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getPrincipal());
        }

        return true;
    }
}
