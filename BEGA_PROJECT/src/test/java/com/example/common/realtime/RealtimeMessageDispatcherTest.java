package com.example.common.realtime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.support.NativeMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

@SuppressWarnings("unchecked")
class RealtimeMessageDispatcherTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
    private final RealtimeMessageDispatcher dispatcher = new RealtimeMessageDispatcher(messagingTemplate);

    @Test
    void dispatchBroadcastSendsToLocalTopicSubscribers() {
        RealtimeMessageEnvelope envelope = RealtimeMessageEnvelope.broadcast(
                "event-1",
                "/topic/party/5",
                objectMapper.valueToTree(Map.of("message", "hello")));

        dispatcher.dispatch(envelope);

        ArgumentCaptor<Map<String, Object>> headersCaptor = ArgumentCaptor.forClass(Map.class);
        verify(messagingTemplate).convertAndSend(
                org.mockito.ArgumentMatchers.eq("/topic/party/5"),
                org.mockito.ArgumentMatchers.eq(envelope.payload()),
                headersCaptor.capture());
        assertEventIdHeader(headersCaptor.getValue(), "event-1");
    }

    @Test
    void dispatchUserMessageSendsToLocalUserSessions() {
        RealtimeMessageEnvelope envelope = RealtimeMessageEnvelope.user(
                "event-2",
                "42",
                "/queue/notifications",
                objectMapper.valueToTree(Map.of("id", 7)));

        dispatcher.dispatch(envelope);

        ArgumentCaptor<Map<String, Object>> headersCaptor = ArgumentCaptor.forClass(Map.class);
        verify(messagingTemplate).convertAndSendToUser(
                org.mockito.ArgumentMatchers.eq("42"),
                org.mockito.ArgumentMatchers.eq("/queue/notifications"),
                org.mockito.ArgumentMatchers.eq(envelope.payload()),
                headersCaptor.capture());
        assertEventIdHeader(headersCaptor.getValue(), "event-2");
    }

    @Test
    void dispatchRejectsUnregisteredBroadcastDestination() {
        RealtimeMessageEnvelope envelope = RealtimeMessageEnvelope.broadcast(
                "event-3",
                "/topic/chat/5",
                objectMapper.createObjectNode());

        assertThatThrownBy(() -> dispatcher.dispatch(envelope))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("destination");
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void dispatchRejectsUserMessageWithoutUserId() {
        RealtimeMessageEnvelope envelope = new RealtimeMessageEnvelope(
                RealtimeMessageEnvelope.CURRENT_VERSION,
                "event-4",
                RealtimeMessageEnvelope.Target.USER,
                "/queue/notifications",
                null,
                objectMapper.createObjectNode());

        assertThatThrownBy(() -> dispatcher.dispatch(envelope))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userId");
        verifyNoInteractions(messagingTemplate);
    }

    @SuppressWarnings("unchecked")
    private void assertEventIdHeader(Map<String, Object> headers, String eventId) {
        Map<String, List<String>> nativeHeaders =
                (Map<String, List<String>>) headers.get(NativeMessageHeaderAccessor.NATIVE_HEADERS);
        org.assertj.core.api.Assertions.assertThat(nativeHeaders)
                .containsEntry("x-realtime-event-id", List.of(eventId));
    }
}
