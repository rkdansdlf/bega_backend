package com.example.common.realtime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class RedisRealtimeMessageSubscriberTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void everyBackendInstanceDispatchesTheSameRedisEventToItsLocalSubscribers() throws Exception {
        RealtimeMessageDispatcher firstInstanceDispatcher = mock(RealtimeMessageDispatcher.class);
        RealtimeMessageDispatcher secondInstanceDispatcher = mock(RealtimeMessageDispatcher.class);
        RedisRealtimeMessageSubscriber firstSubscriber =
                new RedisRealtimeMessageSubscriber(objectMapper, firstInstanceDispatcher);
        RedisRealtimeMessageSubscriber secondSubscriber =
                new RedisRealtimeMessageSubscriber(objectMapper, secondInstanceDispatcher);
        RealtimeMessageEnvelope envelope = RealtimeMessageEnvelope.broadcast(
                "event-1",
                "/topic/dm/9",
                objectMapper.valueToTree(Map.of("messageId", 11)));
        String serializedEnvelope = objectMapper.writeValueAsString(envelope);

        firstSubscriber.handleMessage(serializedEnvelope);
        secondSubscriber.handleMessage(serializedEnvelope);

        verify(firstInstanceDispatcher).dispatch(envelope);
        verify(secondInstanceDispatcher).dispatch(envelope);
    }

    @Test
    void oversizedEnvelopeIsRejectedBeforeDispatch() throws Exception {
        RealtimeMessageDispatcher dispatcher = mock(RealtimeMessageDispatcher.class);
        RedisRealtimeMessageSubscriber subscriber = new RedisRealtimeMessageSubscriber(objectMapper, dispatcher);
        RealtimeMessageEnvelope envelope = RealtimeMessageEnvelope.broadcast(
                "event-oversized",
                "/topic/party/5",
                objectMapper.valueToTree(Map.of("message", "x".repeat(262_145))));
        String oversizedEnvelope = objectMapper.writeValueAsString(envelope);

        subscriber.handleMessage(oversizedEnvelope);

        verifyNoInteractions(dispatcher);
    }

    @Test
    void envelopeWithUnregisteredDestinationIsRejected() throws Exception {
        RealtimeMessageDispatcher dispatcher = mock(RealtimeMessageDispatcher.class);
        RedisRealtimeMessageSubscriber subscriber = new RedisRealtimeMessageSubscriber(objectMapper, dispatcher);
        RealtimeMessageEnvelope envelope = RealtimeMessageEnvelope.broadcast(
                "event-2",
                "/topic/unregistered",
                objectMapper.createObjectNode());

        subscriber.handleMessage(objectMapper.writeValueAsString(envelope));

        verifyNoInteractions(dispatcher);
    }
}
