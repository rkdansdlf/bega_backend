package com.example.common.realtime.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.example.support.RedisIntegrationTestSupport;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class RealtimeAuthorizationCacheRedisIntegrationTest {

    @Test
    @EnabledIfSystemProperty(named = "realtime.redis.integration", matches = "true")
    void allowAndDenyAreSharedAcrossInstancesAndExpire() throws Exception {
        long partyId = positiveRandomId();
        long userId = positiveRandomId();
        String key = RealtimeAuthorizationCache.key(
                RealtimeAuthorizationResource.PARTY, partyId, userId);

        LettuceConnectionFactory connectionFactoryOne = RedisIntegrationTestSupport.connectionFactory();
        LettuceConnectionFactory connectionFactoryTwo = RedisIntegrationTestSupport.connectionFactory();
        SimpleMeterRegistry registryOne = new SimpleMeterRegistry();
        SimpleMeterRegistry registryTwo = new SimpleMeterRegistry();
        StringRedisTemplate redisTemplateOne = new StringRedisTemplate(connectionFactoryOne);
        StringRedisTemplate redisTemplateTwo = new StringRedisTemplate(connectionFactoryTwo);
        Throwable primaryFailure = null;
        try {
            redisTemplateOne.afterPropertiesSet();
            redisTemplateTwo.afterPropertiesSet();

            RealtimeAuthorizationPolicyEvaluator evaluatorOne =
                    mock(RealtimeAuthorizationPolicyEvaluator.class);
            RealtimeAuthorizationPolicyEvaluator evaluatorTwo =
                    mock(RealtimeAuthorizationPolicyEvaluator.class);
            given(evaluatorOne.canAccessParty(partyId, userId)).willReturn(true);
            given(evaluatorTwo.canAccessParty(partyId, userId)).willReturn(false);

            RealtimeAuthorizationService serviceOne = new RealtimeAuthorizationService(
                    new RealtimeAuthorizationCache(
                            redisTemplateOne, registryOne, true, System::nanoTime),
                    evaluatorOne,
                    registryOne);
            RealtimeAuthorizationService serviceTwo = new RealtimeAuthorizationService(
                    new RealtimeAuthorizationCache(
                            redisTemplateTwo, registryTwo, true, System::nanoTime),
                    evaluatorTwo,
                    registryTwo);

            assertThat(serviceOne.canAccessParty(partyId, userId)).isTrue();
            assertThat(serviceTwo.canAccessParty(partyId, userId)).isTrue();
            verify(evaluatorOne, times(1)).canAccessParty(partyId, userId);
            verify(evaluatorTwo, never()).canAccessParty(partyId, userId);

            Long ttlMillis = redisTemplateOne.getExpire(key, TimeUnit.MILLISECONDS);
            assertThat(ttlMillis).isNotNull().isPositive().isLessThanOrEqualTo(5000L);

            Thread.sleep(5200L);

            assertThat(serviceTwo.canAccessParty(partyId, userId)).isFalse();
            verify(evaluatorTwo, times(1)).canAccessParty(partyId, userId);
            assertThat(serviceOne.canAccessParty(partyId, userId)).isFalse();
            verify(evaluatorOne, times(1)).canAccessParty(partyId, userId);
        } catch (Exception | Error exception) {
            primaryFailure = exception;
            throw exception;
        } finally {
            Throwable cleanupFailure = null;
            cleanupFailure = attemptCleanup(cleanupFailure, () -> redisTemplateOne.delete(key));
            cleanupFailure = attemptCleanup(cleanupFailure, registryTwo::close);
            cleanupFailure = attemptCleanup(cleanupFailure, registryOne::close);
            cleanupFailure = attemptCleanup(cleanupFailure, connectionFactoryTwo::destroy);
            cleanupFailure = attemptCleanup(cleanupFailure, connectionFactoryOne::destroy);
            if (cleanupFailure != null) {
                if (primaryFailure != null) {
                    primaryFailure.addSuppressed(cleanupFailure);
                } else if (cleanupFailure instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                } else {
                    throw (Error) cleanupFailure;
                }
            }
        }
    }

    private Throwable attemptCleanup(Throwable cleanupFailure, Runnable cleanup) {
        try {
            cleanup.run();
        } catch (RuntimeException | Error exception) {
            if (cleanupFailure == null) {
                return exception;
            }
            cleanupFailure.addSuppressed(exception);
        }
        return cleanupFailure;
    }

    private long positiveRandomId() {
        long candidate = UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
        return candidate == 0L ? 1L : candidate;
    }
}
