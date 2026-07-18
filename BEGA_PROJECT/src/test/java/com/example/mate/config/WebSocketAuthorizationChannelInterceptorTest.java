package com.example.mate.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.security.Principal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import com.example.common.realtime.authorization.RealtimeAuthorizationService;
import com.example.common.realtime.authorization.RealtimeAuthorizationUnavailableException;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebSocketAuthorizationChannelInterceptor tests")
class WebSocketAuthorizationChannelInterceptorTest {

    @Mock
    private RealtimeAuthorizationService authorizationService;

    private WebSocketAuthorizationChannelInterceptor interceptor;
    private MessageChannel channel;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        interceptor = new WebSocketAuthorizationChannelInterceptor(authorizationService);
        channel = mock(MessageChannel.class);
    }

    @Test
    @DisplayName("CONNECT should reject anonymous session")
    void connect_rejectsAnonymousSession() {
        Message<byte[]> message = message(StompCommand.CONNECT, null, null);

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("로그인");
    }

    @Test
    @DisplayName("CONNECT should reject anonymous principal")
    void connect_rejectsAnonymousPrincipal() {
        AnonymousAuthenticationToken anonymous = new AnonymousAuthenticationToken(
                "key",
                "anonymousUser",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
        Message<byte[]> message = message(StompCommand.CONNECT, null, anonymous);

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("로그인");
    }

    @Test
    @DisplayName("party subscription should allow host")
    void subscribe_partyTopic_allowsHost() {
        given(authorizationService.canAccessParty(10L, 123L)).willReturn(true);

        Message<byte[]> message = message(StompCommand.SUBSCRIBE, "/topic/party/10", principal("123"));

        assertThat(interceptor.preSend(message, channel)).isSameAs(message);
    }

    @Test
    @DisplayName("party subscription should reject non-member")
    void subscribe_partyTopic_rejectsNonMember() {
        given(authorizationService.canAccessParty(10L, 123L)).willReturn(false);

        Message<byte[]> message = message(StompCommand.SUBSCRIBE, "/topic/party/10", principal("123"));

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("파티 참여자");
    }

    @Test
    @DisplayName("dm subscription should allow room participant")
    void subscribe_dmTopic_allowsParticipant() {
        given(authorizationService.canAccessDmRoom(77L, 123L)).willReturn(true);

        Message<byte[]> message = message(StompCommand.SUBSCRIBE, "/topic/dm/77", principal("123"));

        assertThat(interceptor.preSend(message, channel)).isSameAs(message);
    }

    @Test
    @DisplayName("dm subscription should reject non participant")
    void subscribe_dmTopic_rejectsNonParticipant() {
        given(authorizationService.canAccessDmRoom(77L, 123L)).willReturn(false);

        Message<byte[]> message = message(StompCommand.SUBSCRIBE, "/topic/dm/77", principal("123"));

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("대화방 참여자");
    }

    @Test
    @DisplayName("notification user queue should allow authenticated principal")
    void subscribe_notificationUserQueue_allowsAuthenticatedPrincipal() {
        Message<byte[]> message = message(StompCommand.SUBSCRIBE, "/user/queue/notifications", principal("123"));

        assertThat(interceptor.preSend(message, channel)).isSameAs(message);
    }

    @Test
    @DisplayName("legacy notification topic should be denied")
    void subscribe_legacyNotificationTopic_isDenied() {
        Message<byte[]> message = message(StompCommand.SUBSCRIBE, "/topic/notifications/123", principal("123"));

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("경로가 변경");
    }

    @Test
    @DisplayName("unregistered topic subscription should be denied")
    void subscribe_unregisteredTopic_isDenied() {
        Message<byte[]> message = message(StompCommand.SUBSCRIBE, "/topic/chat/10", principal("123"));

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("허용되지 않은 구독 경로");
    }

    @Test
    @DisplayName("chat send should allow approved participant")
    void send_chat_allowsApprovedParticipant() {
        given(authorizationService.canAccessParty(10L, 123L)).willReturn(true);

        Message<byte[]> message = message(StompCommand.SEND, "/app/chat/10", principal("123"));

        assertThat(interceptor.preSend(message, channel)).isSameAs(message);
    }

    @Test
    @DisplayName("party subscribe and send should share the same authorization service method")
    void partySubscribeAndSend_delegateToSharedDecision() {
        given(authorizationService.canAccessParty(10L, 123L)).willReturn(true);

        Message<byte[]> subscribe = message(
                StompCommand.SUBSCRIBE, "/topic/party/10", principal("123"));
        Message<byte[]> send = message(
                StompCommand.SEND, "/app/chat/10", principal("123"));

        assertThat(interceptor.preSend(subscribe, channel)).isSameAs(subscribe);
        assertThat(interceptor.preSend(send, channel)).isSameAs(send);
        verify(authorizationService, times(2)).canAccessParty(10L, 123L);
    }

    @Test
    @DisplayName("authorization backend failure should fail closed")
    void authorizationBackendFailure_failsClosed() {
        RealtimeAuthorizationUnavailableException unavailable =
                new RealtimeAuthorizationUnavailableException(
                        "unavailable", new IllegalStateException("database down"));
        given(authorizationService.canAccessParty(10L, 123L))
                .willThrow(unavailable);

        Message<byte[]> message = message(
                StompCommand.SUBSCRIBE, "/topic/party/10", principal("123"));

        AccessDeniedException denied = assertThrows(
                AccessDeniedException.class,
                () -> interceptor.preSend(message, channel));

        assertThat(denied).hasMessageContaining("권한");
        assertThat(denied.getCause()).isSameAs(unavailable);
    }

    @Test
    @DisplayName("battle vote send should reject anonymous user")
    void send_battleVote_rejectsAnonymousUser() {
        Message<byte[]> message = message(StompCommand.SEND, "/app/battle/vote/20260309HHLG", null);

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("로그인");
    }

    @Test
    @DisplayName("battle subscription should allow an authenticated literal game id")
    void subscribe_battleTopic_allowsAuthenticatedLiteralGameId() {
        Message<byte[]> message = message(
                StompCommand.SUBSCRIBE,
                "/topic/battle/20260309HHLG",
                principal("123"));

        assertThat(interceptor.preSend(message, channel)).isSameAs(message);
    }

    @Test
    @DisplayName("battle subscription should reject anonymous user")
    void subscribe_battleTopic_rejectsAnonymousUser() {
        Message<byte[]> message = message(StompCommand.SUBSCRIBE, "/topic/battle/20260309HHLG", null);

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("로그인");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/topic/battle/*",
            "/topic/battle/?",
            "/topic/battle/{gameId}",
            "/topic/battle/game/extra"
    })
    @DisplayName("battle subscription should reject wildcard and path patterns")
    void subscribe_battleTopic_rejectsNonLiteralGameId(String destination) {
        Message<byte[]> message = message(StompCommand.SUBSCRIBE, destination, principal("123"));

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(AccessDeniedException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/app/battle/vote/*",
            "/app/battle/vote/?",
            "/app/battle/vote/{gameId}",
            "/app/battle/vote/game/extra"
    })
    @DisplayName("battle send should reject wildcard and path patterns")
    void send_battleVote_rejectsNonLiteralGameId(String destination) {
        Message<byte[]> message = message(StompCommand.SEND, destination, principal("123"));

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("unregistered application destination send should be denied")
    void send_unregisteredDestination_isDenied() {
        Message<byte[]> message = message(StompCommand.SEND, "/app/unknown", principal("123"));

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("허용되지 않은 전송 경로");
    }

    @ParameterizedTest
    @ValueSource(strings = { "BEGIN", "COMMIT", "ABORT", "CONNECTED", "MESSAGE", "RECEIPT", "ERROR" })
    @DisplayName("unsupported inbound STOMP commands should be denied")
    void unsupportedInboundCommand_isDenied(String commandName) {
        Message<byte[]> message = message(StompCommand.valueOf(commandName), null, principal("123"));

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("허용되지 않은 STOMP 명령");
    }

    @ParameterizedTest
    @ValueSource(strings = { "UNSUBSCRIBE", "DISCONNECT" })
    @DisplayName("lifecycle STOMP commands should remain allowed")
    void lifecycleCommand_isAllowed(String commandName) {
        Message<byte[]> message = message(StompCommand.valueOf(commandName), null, principal("123"));

        assertThat(interceptor.preSend(message, channel)).isSameAs(message);
    }

    private Message<byte[]> message(StompCommand command, String destination, Principal principal) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        if (destination != null) {
            accessor.setDestination(destination);
        }
        if (principal != null) {
            accessor.setUser(principal);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Principal principal(String name) {
        return () -> name;
    }
}
