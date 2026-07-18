package com.example.common.realtime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class RedisRealtimeMessageMultiInstanceIntegrationTest {

    private static final Duration SUBSCRIPTION_TIMEOUT = Duration.ofSeconds(5);

    @Test
    @EnabledIfSystemProperty(named = "realtime.redis.integration", matches = "true")
    void broadcastAndUserPublishFanOutToTwoBackendInstances() throws Exception {
        String host = System.getProperty("realtime.redis.host", "127.0.0.1");
        int port = Integer.parseInt(System.getProperty("realtime.redis.port", "16379"));
        String channel = "bega:realtime:integration:" + UUID.randomUUID();

        LettuceConnectionFactory connectionFactoryOne = connectionFactory(host, port);
        LettuceConnectionFactory connectionFactoryTwo = connectionFactory(host, port);
        RedisMessageListenerContainer containerOne = null;
        RedisMessageListenerContainer containerTwo = null;

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            SimpMessagingTemplate messagingTemplateOne = mock(SimpMessagingTemplate.class);
            SimpMessagingTemplate messagingTemplateTwo = mock(SimpMessagingTemplate.class);
            RedisRealtimeMessageSubscriber subscriberOne = new RedisRealtimeMessageSubscriber(
                    objectMapper,
                    new RealtimeMessageDispatcher(messagingTemplateOne));
            RedisRealtimeMessageSubscriber subscriberTwo = new RedisRealtimeMessageSubscriber(
                    objectMapper,
                    new RealtimeMessageDispatcher(messagingTemplateTwo));
            CountDownLatch subscriberOneReady = new CountDownLatch(1);
            CountDownLatch subscriberTwoReady = new CountDownLatch(1);

            containerOne = listenerContainer(
                    connectionFactoryOne,
                    channel,
                    subscriberOne,
                    subscriberOneReady);
            containerTwo = listenerContainer(
                    connectionFactoryTwo,
                    channel,
                    subscriberTwo,
                    subscriberTwoReady);

            StringRedisTemplate redisTemplate = new StringRedisTemplate(connectionFactoryOne);
            redisTemplate.afterPropertiesSet();
            awaitSubscriptions(
                    redisTemplate,
                    channel,
                    objectMapper,
                    subscriberOneReady,
                    subscriberTwoReady);
            clearInvocations(messagingTemplateOne, messagingTemplateTwo);

            RedisRealtimeMessagePublisher publisher = new RedisRealtimeMessagePublisher(
                    redisTemplate,
                    objectMapper,
                    new RealtimeMessageDispatcher(messagingTemplateOne),
                    channel);
            publisher.publish(RealtimeMessageEnvelope.broadcast(
                    "outbox-broadcast-event",
                    "/topic/dm/9",
                    objectMapper.valueToTree(Map.of("message", "fanout"))));

            verify(messagingTemplateOne, timeout(5000)).convertAndSend(
                    eq("/topic/dm/9"),
                    any(JsonNode.class),
                    anyMap());
            verify(messagingTemplateTwo, timeout(5000)).convertAndSend(
                    eq("/topic/dm/9"),
                    any(JsonNode.class),
                    anyMap());

            clearInvocations(messagingTemplateOne, messagingTemplateTwo);
            publisher.publish(RealtimeMessageEnvelope.user(
                    "outbox-user-event",
                    "42",
                    "/queue/notifications",
                    objectMapper.valueToTree(Map.of("notificationId", 7))));

            verify(messagingTemplateOne, timeout(5000)).convertAndSendToUser(
                    eq("42"),
                    eq("/queue/notifications"),
                    any(JsonNode.class),
                    anyMap());
            verify(messagingTemplateTwo, timeout(5000)).convertAndSendToUser(
                    eq("42"),
                    eq("/queue/notifications"),
                    any(JsonNode.class),
                    anyMap());
        } finally {
            stop(containerTwo);
            stop(containerOne);
            connectionFactoryTwo.destroy();
            connectionFactoryOne.destroy();
        }
    }

    private LettuceConnectionFactory connectionFactory(String host, int port) {
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(host, port);
        connectionFactory.afterPropertiesSet();
        return connectionFactory;
    }

    private RedisMessageListenerContainer listenerContainer(
            LettuceConnectionFactory connectionFactory,
            String channel,
            RedisRealtimeMessageSubscriber subscriber,
            CountDownLatch readinessLatch) {
        MessageListener listener = (message, pattern) -> {
            subscriber.onMessage(message, pattern);
            readinessLatch.countDown();
        };
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(listener, new ChannelTopic(channel));
        container.afterPropertiesSet();
        container.start();
        return container;
    }

    private void awaitSubscriptions(
            StringRedisTemplate redisTemplate,
            String channel,
            ObjectMapper objectMapper,
            CountDownLatch subscriberOneReady,
            CountDownLatch subscriberTwoReady) throws Exception {
        long deadline = System.nanoTime() + SUBSCRIPTION_TIMEOUT.toNanos();
        String probe = objectMapper.writeValueAsString(RealtimeMessageEnvelope.broadcast(
                UUID.randomUUID().toString(),
                "/topic/party/1",
                objectMapper.createObjectNode()));

        while (System.nanoTime() < deadline
                && (subscriberOneReady.getCount() > 0 || subscriberTwoReady.getCount() > 0)) {
            redisTemplate.convertAndSend(channel, probe);
            Thread.sleep(50);
        }

        if (subscriberOneReady.getCount() > 0 || subscriberTwoReady.getCount() > 0) {
            throw new IllegalStateException("Redis subscribers did not become ready within " + SUBSCRIPTION_TIMEOUT);
        }
    }

    private void stop(RedisMessageListenerContainer container) throws Exception {
        if (container != null) {
            container.stop();
            container.destroy();
        }
    }
}
