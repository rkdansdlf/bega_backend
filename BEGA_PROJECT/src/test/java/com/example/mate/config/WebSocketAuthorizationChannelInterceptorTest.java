package com.example.mate.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.security.Principal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

import com.example.dm.service.DmRoomService;
import com.example.mate.entity.Party;
import com.example.mate.entity.PartyApplication;
import com.example.mate.repository.PartyApplicationRepository;
import com.example.mate.repository.PartyRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebSocketAuthorizationChannelInterceptor tests")
class WebSocketAuthorizationChannelInterceptorTest {

    @Mock
    private PartyRepository partyRepository;

    @Mock
    private PartyApplicationRepository partyApplicationRepository;

    @Mock
    private DmRoomService dmRoomService;

    private WebSocketAuthorizationChannelInterceptor interceptor;
    private MessageChannel channel;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        interceptor = new WebSocketAuthorizationChannelInterceptor(partyRepository, partyApplicationRepository, dmRoomService);
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
        given(partyRepository.findById(10L)).willReturn(java.util.Optional.of(
                Party.builder().id(10L).hostId(123L).build()));

        Message<byte[]> message = message(StompCommand.SUBSCRIBE, "/topic/party/10", principal("123"));

        assertThat(interceptor.preSend(message, channel)).isSameAs(message);
    }

    @Test
    @DisplayName("party subscription should reject non-member")
    void subscribe_partyTopic_rejectsNonMember() {
        given(partyRepository.findById(10L)).willReturn(java.util.Optional.of(
                Party.builder().id(10L).hostId(999L).build()));
        given(partyApplicationRepository.findByPartyIdAndApplicantId(10L, 123L))
                .willReturn(java.util.Optional.empty());

        Message<byte[]> message = message(StompCommand.SUBSCRIBE, "/topic/party/10", principal("123"));

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("파티 참여자");
    }

    @Test
    @DisplayName("dm subscription should allow room participant")
    void subscribe_dmTopic_allowsParticipant() {
        given(dmRoomService.canSubscribe(77L, 123L)).willReturn(true);

        Message<byte[]> message = message(StompCommand.SUBSCRIBE, "/topic/dm/77", principal("123"));

        assertThat(interceptor.preSend(message, channel)).isSameAs(message);
    }

    @Test
    @DisplayName("dm subscription should reject non participant")
    void subscribe_dmTopic_rejectsNonParticipant() {
        given(dmRoomService.canSubscribe(77L, 123L)).willReturn(false);

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
    @DisplayName("chat send should allow approved participant")
    void send_chat_allowsApprovedParticipant() {
        given(partyRepository.findById(10L)).willReturn(java.util.Optional.of(
                Party.builder().id(10L).hostId(999L).build()));
        given(partyApplicationRepository.findByPartyIdAndApplicantId(10L, 123L))
                .willReturn(java.util.Optional.of(
                        PartyApplication.builder().partyId(10L).applicantId(123L).isApproved(true).build()));

        Message<byte[]> message = message(StompCommand.SEND, "/app/chat/10", principal("123"));

        assertThat(interceptor.preSend(message, channel)).isSameAs(message);
    }

    @Test
    @DisplayName("battle vote send should reject anonymous user")
    void send_battleVote_rejectsAnonymousUser() {
        Message<byte[]> message = message(StompCommand.SEND, "/app/battle/vote/20260309HHLG", null);

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("로그인");
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
