package com.example.common.ratelimit;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
class RateLimitServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Test
    void redisFailureFallsOpenByDefault() {
        RateLimitService service = new RateLimitService(redisTemplate);
        when(redisTemplate.execute(any(), anyList(), anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("redis down"));

        assertThat(service.isAllowed("rate:test", 10, 60)).isTrue();
    }

    @Test
    void redisFailureFailsClosedForProtectedAuthEndpoints() {
        RateLimitService service = new RateLimitService(redisTemplate);
        when(redisTemplate.execute(any(), anyList(), anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("redis down"));

        assertThat(service.isAllowed("rate:test", 10, 60, true)).isFalse();
    }

    @Test
    void redisFailureDoesNotLogRateLimitKeyOrExceptionMessage() {
        RateLimitService service = new RateLimitService(redisTemplate);
        String email = "private@example.test";
        String key = "rate:limit:auth:oauth-email-challenge-email:" + email;
        String exceptionMessage = "redis error for " + key;
        when(redisTemplate.execute(any(), anyList(), anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException(exceptionMessage));

        Logger logger = (Logger) LoggerFactory.getLogger(RateLimitService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            assertThat(service.isAllowed(key, 3, 3600, true)).isFalse();

            assertThat(appender.list)
                    .extracting(ILoggingEvent::getFormattedMessage)
                    .singleElement()
                    .asString()
                    .contains("failClosed=true", "RuntimeException")
                    .doesNotContain(key, email, exceptionMessage);
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
