package com.example.support;

import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * Test-scoped Redis configuration that provides mock beans
 * when RedisAutoConfiguration is excluded.
 *
 * Production beans in RedisConfig and CacheConfig depend on
 * RedisConnectionFactory; this satisfies those dependencies
 * without requiring a running Redis instance.
 */
@Configuration
public class TestRedisConfiguration {

    @Bean
    @ConditionalOnMissingBean(RedisConnectionFactory.class)
    public RedisConnectionFactory redisConnectionFactory() {
        return Mockito.mock(RedisConnectionFactory.class);
    }

    @Bean
    @ConditionalOnMissingBean(StringRedisTemplate.class)
    public StringRedisTemplate stringRedisTemplate() {
        StringRedisTemplate mock = Mockito.mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = Mockito.mock(ValueOperations.class);
        Mockito.lenient().when(mock.opsForValue()).thenReturn(valueOps);
        return mock;
    }
}
