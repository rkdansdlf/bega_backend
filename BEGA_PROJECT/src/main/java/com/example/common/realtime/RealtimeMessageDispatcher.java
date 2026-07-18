package com.example.common.realtime;

import java.util.Map;

import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RealtimeMessageDispatcher {

    private final SimpMessagingTemplate messagingTemplate;

    public void dispatch(RealtimeMessageEnvelope envelope) {
        RealtimeMessageEnvelopeValidator.validate(envelope);
        Map<String, Object> headers = eventHeaders(envelope.eventId());

        switch (envelope.target()) {
            case BROADCAST -> messagingTemplate.convertAndSend(
                    envelope.destination(),
                    envelope.payload(),
                    headers);
            case USER -> messagingTemplate.convertAndSendToUser(
                    envelope.userId(),
                    envelope.destination(),
                    envelope.payload(),
                    headers);
        }
    }

    private Map<String, Object> eventHeaders(String eventId) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        accessor.setNativeHeader("x-realtime-event-id", eventId);
        accessor.setLeaveMutable(true);
        return accessor.getMessageHeaders();
    }
}
