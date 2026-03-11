package com.example.mate.config;

import java.security.Principal;
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

import com.example.mate.entity.Party;
import com.example.mate.repository.PartyApplicationRepository;
import com.example.mate.repository.PartyRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class WebSocketAuthorizationChannelInterceptor implements ChannelInterceptor {

    private static final Pattern PARTY_TOPIC_PATTERN = Pattern.compile("^/topic/party/(\\d+)$");
    private static final Pattern CHAT_SEND_PATTERN = Pattern.compile("^/app/chat/(\\d+)$");
    private static final Pattern BATTLE_TOPIC_PATTERN = Pattern.compile("^/topic/battle/[^/]+$");
    private static final Pattern BATTLE_SEND_PATTERN = Pattern.compile("^/app/battle/vote/[^/]+$");
    private static final String USER_NOTIFICATION_DESTINATION = "/user/queue/notifications";
    private static final String LEGACY_NOTIFICATION_TOPIC_PREFIX = "/topic/notifications/";

    private final PartyRepository partyRepository;
    private final PartyApplicationRepository partyApplicationRepository;

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
            case CONNECT -> requireAuthenticated(principal, "WebSocket 연결에는 로그인이 필요합니다.");
            case SUBSCRIBE -> authorizeSubscribe(principal, destination);
            case SEND -> authorizeSend(principal, destination);
            default -> {
                // no-op
            }
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
            return;
        }

        if (BATTLE_TOPIC_PATTERN.matcher(destination).matches()) {
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
            if (!isPartyMember(userId, partyId)) {
                throw new AccessDeniedException("파티 참여자만 채팅을 구독할 수 있습니다.");
            }
        }
    }

    private void authorizeSend(Principal principal, String destination) {
        if (destination == null || destination.isBlank()) {
            return;
        }

        Matcher chatMatcher = CHAT_SEND_PATTERN.matcher(destination);
        if (chatMatcher.matches()) {
            Long userId = requireAuthenticatedUserId(principal, "채팅 전송에는 로그인이 필요합니다.");
            Long partyId = Long.valueOf(chatMatcher.group(1));
            if (!isPartyMember(userId, partyId)) {
                throw new AccessDeniedException("파티 참여자만 채팅을 전송할 수 있습니다.");
            }
            return;
        }

        if (BATTLE_SEND_PATTERN.matcher(destination).matches()) {
            requireAuthenticated(principal, "응원 배틀 투표에는 로그인이 필요합니다.");
        }
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

    private boolean isPartyMember(Long userId, Long partyId) {
        return partyRepository.findById(partyId)
                .map(party -> isHost(userId, party) || isApprovedParticipant(userId, partyId))
                .orElse(false);
    }

    private boolean isHost(Long userId, Party party) {
        return party != null && party.getHostId() != null && party.getHostId().equals(userId);
    }

    private boolean isApprovedParticipant(Long userId, Long partyId) {
        return partyApplicationRepository.findByPartyIdAndApplicantId(partyId, userId)
                .map(application -> Boolean.TRUE.equals(application.getIsApproved()))
                .orElse(false);
    }
}
