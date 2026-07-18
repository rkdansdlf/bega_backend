package com.example.mate.config;

import java.security.Principal;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.example.common.realtime.authorization.RealtimeAuthorizationService;
import com.example.common.realtime.authorization.RealtimeAuthorizationUnavailableException;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class WebSocketAuthorizationChannelInterceptor implements ChannelInterceptor {

    private static final Pattern PARTY_TOPIC_PATTERN = Pattern.compile("^/topic/party/(\\d+)$");
    private static final Pattern DM_TOPIC_PATTERN = Pattern.compile("^/topic/dm/(\\d+)$");
    private static final Pattern CHAT_SEND_PATTERN = Pattern.compile("^/app/chat/(\\d+)$");
    private static final String BATTLE_GAME_ID_PATTERN = "[A-Za-z0-9_-]{1,64}";
    private static final Pattern BATTLE_TOPIC_PATTERN = Pattern.compile(
            "^/topic/battle/" + BATTLE_GAME_ID_PATTERN + "$");
    private static final Pattern BATTLE_SEND_PATTERN = Pattern.compile(
            "^/app/battle/vote/" + BATTLE_GAME_ID_PATTERN + "$");
    private static final String USER_NOTIFICATION_DESTINATION = "/user/queue/notifications";
    private static final String LEGACY_NOTIFICATION_TOPIC_PREFIX = "/topic/notifications/";

    private final RealtimeAuthorizationService authorizationService;

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        Principal principal = ensurePrincipal(accessor);
        StompCommand command = accessor.getCommand();
        String destination = accessor.getDestination();

        switch (command) {
            case CONNECT, STOMP -> requireAuthenticated(principal, "WebSocket 연결에는 로그인이 필요합니다.");
            case SUBSCRIBE -> authorizeSubscribe(principal, destination);
            case SEND -> authorizeSend(principal, destination);
            case UNSUBSCRIBE, DISCONNECT, ACK, NACK -> {
                // Authenticated session lifecycle and acknowledgement commands.
            }
            default -> throw new AccessDeniedException("허용되지 않은 STOMP 명령입니다.");
        }

        return message;
    }

    private Principal ensurePrincipal(StompHeaderAccessor accessor) {
        Principal principal = accessor.getUser();
        if (principal != null) {
            return principal;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())) {
            accessor.setUser(authentication);
            return authentication;
        }

        return null;
    }

    private void authorizeSubscribe(Principal principal, String destination) {
        if (destination == null || destination.isBlank()) {
            throw new AccessDeniedException("허용되지 않은 구독 경로입니다.");
        }

        if (BATTLE_TOPIC_PATTERN.matcher(destination).matches()) {
            requireAuthenticated(principal, "응원 배틀 구독에는 로그인이 필요합니다.");
            return;
        }

        if (destination.startsWith(LEGACY_NOTIFICATION_TOPIC_PREFIX)) {
            throw new AccessDeniedException("실시간 알림 구독 경로가 변경되었습니다.");
        }

        if (USER_NOTIFICATION_DESTINATION.equals(destination)) {
            requireAuthenticated(principal, "알림 구독에는 로그인이 필요합니다.");
            return;
        }

        Matcher partyMatcher = PARTY_TOPIC_PATTERN.matcher(destination);
        if (partyMatcher.matches()) {
            Long userId = requireAuthenticatedUserId(principal, "파티 채팅 구독에는 로그인이 필요합니다.");
            Long partyId = Long.valueOf(partyMatcher.group(1));
            if (!evaluate(() -> authorizationService.canAccessParty(partyId, userId))) {
                throw new AccessDeniedException("파티 참여자만 채팅을 구독할 수 있습니다.");
            }
            return;
        }

        Matcher dmMatcher = DM_TOPIC_PATTERN.matcher(destination);
        if (dmMatcher.matches()) {
            Long userId = requireAuthenticatedUserId(principal, "DM 구독에는 로그인이 필요합니다.");
            Long roomId = Long.valueOf(dmMatcher.group(1));
            if (!evaluate(() -> authorizationService.canAccessDmRoom(roomId, userId))) {
                throw new AccessDeniedException("대화방 참여자만 DM을 구독할 수 있습니다.");
            }
            return;
        }

        throw new AccessDeniedException("허용되지 않은 구독 경로입니다.");
    }

    private void authorizeSend(Principal principal, String destination) {
        if (destination == null || destination.isBlank()) {
            throw new AccessDeniedException("허용되지 않은 전송 경로입니다.");
        }

        Matcher chatMatcher = CHAT_SEND_PATTERN.matcher(destination);
        if (chatMatcher.matches()) {
            Long userId = requireAuthenticatedUserId(principal, "채팅 전송에는 로그인이 필요합니다.");
            Long partyId = Long.valueOf(chatMatcher.group(1));
            if (!evaluate(() -> authorizationService.canAccessParty(partyId, userId))) {
                throw new AccessDeniedException("파티 참여자만 채팅을 전송할 수 있습니다.");
            }
            return;
        }

        if (BATTLE_SEND_PATTERN.matcher(destination).matches()) {
            requireAuthenticated(principal, "응원 배틀 투표에는 로그인이 필요합니다.");
            return;
        }

        throw new AccessDeniedException("허용되지 않은 전송 경로입니다.");
    }

    private void requireAuthenticated(Principal principal, String message) {
        if (!isAuthenticatedPrincipal(principal)) {
            throw new AccessDeniedException(message);
        }
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

    private Long requireAuthenticatedUserId(Principal principal, String message) {
        requireAuthenticated(principal, message);
        try {
            return Long.valueOf(principal.getName());
        } catch (NumberFormatException e) {
            throw new AccessDeniedException("인증된 사용자 정보가 올바르지 않습니다.");
        }
    }

    private boolean evaluate(BooleanSupplier decision) {
        try {
            return decision.getAsBoolean();
        } catch (RealtimeAuthorizationUnavailableException exception) {
            throw new AccessDeniedException("실시간 권한을 확인할 수 없습니다.", exception);
        }
    }
}
