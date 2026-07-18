package com.example.common.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;

import com.example.auth.repository.UserRepository;
import com.example.auth.service.AuthSecurityMonitoringService;
import com.example.auth.service.OAuth2StateService;
import com.example.common.config.CacheConfig;
import com.example.common.config.RedisConfig;
import com.example.common.ratelimit.RateLimitService;
import com.example.support.RedisIntegrationTestSupport;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

class RedisAclWorkloadIntegrationTest {

    @Test
    @EnabledIfSystemProperty(named = "realtime.redis.integration", matches = "true")
    void approvedBackendWorkloadsSucceedWithProductionAcl() {
        String prefix = "acl:integration:" + UUID.randomUUID();
        String stringKey = prefix + ":string";
        String absentKey = prefix + ":absent";
        String setKey = prefix + ":set";
        String sortedSetKey = prefix + ":sorted-set";
        String transactionKey = prefix + ":transaction";
        String rateLimitKey = prefix + ":rate-limit";
        String cacheKey = prefix + ":cache";

        LettuceConnectionFactory connectionFactory = RedisIntegrationTestSupport.connectionFactory();
        StringRedisTemplate redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        String oauthStateId = null;
        Cache cache = null;
        Throwable primaryFailure = null;
        try {
            redisTemplate.opsForValue().set(stringKey, "one", Duration.ofSeconds(30));
            assertThat(redisTemplate.opsForValue().get(stringKey)).isEqualTo("one");
            assertThat(redisTemplate.getExpire(stringKey)).isPositive();
            assertThat(redisTemplate.opsForValue().setIfAbsent(absentKey, "first", Duration.ofSeconds(30)))
                    .isTrue();
            assertThat(redisTemplate.opsForValue().setIfAbsent(absentKey, "second", Duration.ofSeconds(30)))
                    .isFalse();
            assertThat(redisTemplate.opsForValue().multiGet(List.of(stringKey, absentKey)))
                    .containsExactly("one", "first");

            assertThat(redisTemplate.opsForSet().add(setKey, "alpha", "beta")).isEqualTo(2L);
            assertThat(redisTemplate.opsForSet().members(setKey)).containsExactlyInAnyOrder("alpha", "beta");
            assertThat(redisTemplate.opsForZSet().add(sortedSetKey, "first", 1.0)).isTrue();
            assertThat(redisTemplate.opsForZSet().range(sortedSetKey, 0, -1)).containsExactly("first");

            List<Object> transactionResult = redisTemplate.execute(new SessionCallback<>() {
                @Override
                @SuppressWarnings({ "rawtypes", "unchecked" })
                public List<Object> execute(RedisOperations operations) {
                    operations.multi();
                    operations.opsForValue().set(transactionKey, "committed");
                    return operations.exec();
                }
            });
            assertThat(transactionResult).isNotNull();
            assertThat(redisTemplate.opsForValue().get(transactionKey)).isEqualTo("committed");

            ObjectMapper objectMapper = new ObjectMapper()
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            OAuth2StateService stateService = new OAuth2StateService(
                    redisTemplate,
                    objectMapper,
                    mock(UserRepository.class),
                    mock(AuthSecurityMonitoringService.class));
            oauthStateId = stateService.saveState(77L);
            assertThat(stateService.peekUserId(oauthStateId)).isEqualTo(77L);
            String oauthKey = "oauth2:state:" + oauthStateId;
            assertThat(redisTemplate.opsForValue().getAndDelete(oauthKey)).isNotNull();
            assertThat(redisTemplate.opsForValue().get(oauthKey)).isNull();

            RateLimitService rateLimitService = new RateLimitService(redisTemplate);
            assertThat(rateLimitService.isAllowed(rateLimitKey, 1, 60, true)).isTrue();
            assertThat(rateLimitService.isAllowed(rateLimitKey, 1, 60, true)).isFalse();

            RedisSerializer<Object> serializer =
                    new RedisConfig().redisValueSerializer(objectMapper);
            CacheManager cacheManager = new CacheConfig().redisCacheManager(connectionFactory, serializer);
            cache = Objects.requireNonNull(cacheManager.getCache(CacheConfig.LIVE_GAME_STATUS));
            String cachedValue = "ready";
            cache.put(cacheKey, cachedValue);
            String redisCacheKey = CacheConfig.LIVE_GAME_STATUS + "::" + cacheKey;
            assertThat(redisTemplate.hasKey(redisCacheKey)).isTrue();
            Cache.ValueWrapper cached = cache.get(cacheKey);
            assertThat(cached).isNotNull();
            assertThat(cached.get()).isEqualTo(cachedValue);
            assertThat(cache.evictIfPresent(cacheKey)).isTrue();
            assertThat(redisTemplate.hasKey(redisCacheKey)).isFalse();
            assertThat(cache.get(cacheKey)).isNull();
        } catch (RuntimeException | Error exception) {
            primaryFailure = exception;
            throw exception;
        } finally {
            Throwable cleanupFailure = null;
            Cache cacheToCleanup = cache;
            String oauthStateToCleanup = oauthStateId;
            if (cacheToCleanup != null) {
                cleanupFailure = attemptCleanup(
                        cleanupFailure,
                        () -> cacheToCleanup.evict(cacheKey));
            }
            if (oauthStateToCleanup != null) {
                cleanupFailure = attemptCleanup(
                        cleanupFailure,
                        () -> redisTemplate.delete("oauth2:state:" + oauthStateToCleanup));
            }
            cleanupFailure = attemptCleanup(
                    cleanupFailure,
                    () -> redisTemplate.delete(Set.of(
                            stringKey,
                            absentKey,
                            setKey,
                            sortedSetKey,
                            transactionKey,
                            rateLimitKey)));
            cleanupFailure = attemptCleanup(cleanupFailure, connectionFactory::destroy);
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
}
