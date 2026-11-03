package com.example.cheerboard.service;

import java.util.Arrays;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.core.SessionCallback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class RedisPostServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private ZSetOperations<String, Object> zSetOperations;

    @Mock
    private RedisSerializer<Object> valueSerializer;

    @Test
    @DisplayName("feed Redis stats maps pipelined view counts and hot statuses by post id")
    void getFeedRedisStatsMapsPipelineResultsByPostId() {
        RedisPostService redisPostService = new RedisPostService(redisTemplate);
        when(redisTemplate.executePipelined(any(SessionCallback.class)))
                .thenReturn(List.of(
                        Arrays.asList(12L, 3, null),
                        Arrays.asList(true, false, null)));

        RedisPostService.FeedRedisStats stats = redisPostService.getFeedRedisStats(List.of(10L, 20L, 30L));

        assertThat(stats.viewCounts()).containsEntry(10L, 12).containsEntry(20L, 3);
        assertThat(stats.viewCounts()).doesNotContainKey(30L);
        assertThat(stats.hotStatuses()).containsEntry(10L, true).containsEntry(20L, false);
        assertThat(stats.hotStatuses()).doesNotContainKey(30L);
    }

    @Test
    @DisplayName("feed Redis stats returns empty maps when no post ids are provided")
    void getFeedRedisStatsReturnsEmptyMapsForEmptyIds() {
        RedisPostService redisPostService = new RedisPostService(redisTemplate);

        RedisPostService.FeedRedisStats stats = redisPostService.getFeedRedisStats(List.of());

        assertThat(stats.viewCounts()).isEmpty();
        assertThat(stats.hotStatuses()).isEmpty();
    }

    @Test
    @DisplayName("sync 조회수 조회는 Redis key 부재를 빈 결과로 표현한다")
    void getViewCountForSyncReturnsEmptyWhenKeyIsMissing() {
        RedisPostService redisPostService = new RedisPostService(redisTemplate);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("post:views:42")).thenReturn(null);

        assertThat(redisPostService.getViewCountForSync(42L)).isEmpty();
    }

    @Test
    @DisplayName("sync 조회수 조회는 Redis read 오류를 숨기지 않는다")
    void getViewCountForSyncPropagatesRedisReadFailure() {
        RedisPostService redisPostService = new RedisPostService(redisTemplate);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("post:views:42")).thenThrow(new IllegalStateException("Redis unavailable"));

        assertThatThrownBy(() -> redisPostService.getViewCountForSync(42L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Redis unavailable");
    }

    @Test
    @DisplayName("sync hot-score 쓰기 오류는 재시도를 위해 호출자에게 전파한다")
    void updateHotScoreForSyncPropagatesRedisWriteFailure() {
        RedisPostService redisPostService = new RedisPostService(redisTemplate);
        IllegalStateException redisFailure = new IllegalStateException("Redis unavailable");
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        doThrow(redisFailure).when(zSetOperations).add(any(), any(), anyDouble());

        assertThatThrownBy(() -> redisPostService.updateHotScoreForSync(
                42L, 1.25, PopularFeedAlgorithm.TIME_DECAY))
                .isSameAs(redisFailure);
    }

    @Test
    @DisplayName("sync HOT 상태 캐시 오류는 재시도 호출자에게 전파한다")
    void cacheHotStatusForSyncPropagatesRedisWriteFailure() {
        RedisPostService redisPostService = new RedisPostService(redisTemplate);
        IllegalStateException redisFailure = new IllegalStateException("Redis unavailable");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        doThrow(redisFailure).when(valueOperations)
                .set(eq("post:hot:42"), eq(true), any(java.time.Duration.class));

        assertThatThrownBy(() -> redisPostService.cacheHotStatusForSync(42L, true))
                .isSameAs(redisFailure);
    }

    @Test
    @DisplayName("sync HOT 목록 제거 오류는 재시도 호출자에게 전파한다")
    void removeFromHotListForSyncPropagatesRedisWriteFailure() {
        RedisPostService redisPostService = new RedisPostService(redisTemplate);
        IllegalStateException redisFailure = new IllegalStateException("Redis unavailable");
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        doThrow(redisFailure).when(zSetOperations).remove("posts:hot:list", "42");

        assertThatThrownBy(() -> redisPostService.removeFromHotListForSync(42L))
                .isSameAs(redisFailure);
    }

    @Test
    @DisplayName("일반 HOT 상태 캐시는 Redis 오류를 기존 best-effort 계약대로 숨긴다")
    void cacheHotStatusRemainsBestEffort() {
        RedisPostService redisPostService = new RedisPostService(redisTemplate);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        doThrow(new IllegalStateException("Redis unavailable")).when(valueOperations)
                .set("post:hot:42", true, Duration.ofMinutes(10));

        assertThatCode(() -> redisPostService.cacheHotStatus(42L, true)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("일반 HOT 점수 갱신은 Redis 오류를 기존 best-effort 계약대로 숨긴다")
    void updateHotScoreRemainsBestEffort() {
        RedisPostService redisPostService = new RedisPostService(redisTemplate);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        doThrow(new IllegalStateException("Redis unavailable"))
                .when(zSetOperations).add("posts:hot:list", "42", 1.25);

        assertThatCode(() -> redisPostService.updateHotScore(
                42L, 1.25, PopularFeedAlgorithm.TIME_DECAY)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("일반 HOT 목록 제거는 Redis 오류를 기존 best-effort 계약대로 숨긴다")
    void removeFromHotListRemainsBestEffort() {
        RedisPostService redisPostService = new RedisPostService(redisTemplate);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        doThrow(new IllegalStateException("Redis unavailable"))
                .when(zSetOperations).remove("posts:hot:list", "42");

        assertThatCode(() -> redisPostService.removeFromHotList(42L)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("동일 사용자의 조회수 중복 방지와 카운터·dirty 등록을 한 Lua 작업으로 처리한다")
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void incrementViewCountUsesAtomicDeduplicationScript() {
        RedisPostService redisPostService = new RedisPostService(redisTemplate);
        stubStringRedisValueSerializer();

        redisPostService.incrementViewCount(42L, 7L);

        ArgumentCaptor<RedisScript> scriptCaptor = ArgumentCaptor.forClass(RedisScript.class);
        ArgumentCaptor<byte[]> argumentCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(redisTemplate).execute(
                scriptCaptor.capture(),
                any(RedisSerializer.class),
                any(RedisSerializer.class),
                eq(List.of("post:viewed:42", "post:views:42", "posts:dirty:views")),
                argumentCaptor.capture(),
                argumentCaptor.capture(),
                argumentCaptor.capture());

        assertThat(argumentCaptor.getAllValues().get(0)).containsExactly("7".getBytes(StandardCharsets.UTF_8));
        assertThat(argumentCaptor.getAllValues().get(1)).containsExactly("86400".getBytes(StandardCharsets.UTF_8));
        assertThat(argumentCaptor.getAllValues().get(2)).containsExactly("42".getBytes(StandardCharsets.UTF_8));

        String script = scriptCaptor.getValue().getScriptAsString();
        int viewedAddIndex = script.indexOf("redis.call('SADD', KEYS[1], ARGV[1])");
        int duplicateCheckIndex = script.indexOf("if added == 0 then");
        int duplicateReturnIndex = script.indexOf("return 0");
        int counterIncrementIndex = script.indexOf("redis.call('INCR', KEYS[2])");
        int counterPersistIndex = script.indexOf("redis.call('PERSIST', KEYS[2])");
        int dirtyRegistrationIndex = script.indexOf("redis.call('SADD', KEYS[3], ARGV[3])");

        assertThat(viewedAddIndex).isGreaterThanOrEqualTo(0);
        assertThat(duplicateCheckIndex).isGreaterThan(viewedAddIndex);
        assertThat(duplicateReturnIndex).isGreaterThan(duplicateCheckIndex);
        assertThat(counterIncrementIndex).isGreaterThan(duplicateReturnIndex);
        assertThat(counterPersistIndex).isGreaterThan(counterIncrementIndex);
        assertThat(dirtyRegistrationIndex).isGreaterThan(counterPersistIndex);
        verify(redisTemplate, never()).opsForSet();
        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    @DisplayName("조회수 sync claim은 안정적인 sequence batch를 원자적으로 만든다")
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void claimViewCountSyncBatchUsesAtomicScriptAndStableInFlightBatch() {
        RedisPostService redisPostService = new RedisPostService(redisTemplate);
        stubStringRedisValueSerializer();
        doReturn("12|7").when(redisTemplate).execute(
                any(RedisScript.class),
                any(RedisSerializer.class),
                any(RedisSerializer.class),
                any(List.class),
                any(Object.class),
                any(Object.class));

        assertThat(redisPostService.claimViewCountSyncBatch(42L, 10L))
                .contains(new RedisPostService.ViewCountSyncBatch(12L, 7));

        ArgumentCaptor<RedisScript> scriptCaptor = ArgumentCaptor.forClass(RedisScript.class);
        verify(redisTemplate).execute(
                scriptCaptor.capture(),
                any(RedisSerializer.class),
                any(RedisSerializer.class),
                eq(List.of(
                        "post:views:42",
                        "post:views:sync:inflight:42",
                        "post:views:sync:sequence:42",
                        "posts:dirty:views")),
                eq("10".getBytes(StandardCharsets.UTF_8)),
                eq("42".getBytes(StandardCharsets.UTF_8)));

        String script = scriptCaptor.getValue().getScriptAsString();
        assertThat(script).contains(
                "redis.call('GET', KEYS[2])",
                "redis.call('INCR', KEYS[3])",
                "redis.call('SET', KEYS[2], batch)",
                "redis.call('DEL', KEYS[1])",
                "redis.call('SADD', KEYS[4], ARGV[2])");
        assertThat(script).doesNotContain("redis.call('EXPIRE'");
    }

    @Test
    @DisplayName("조회수 sync ack는 같은 sequence만 완료하고 남은 work 표식을 보존한다")
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void acknowledgeViewCountSyncBatchUsesSequenceGuardAndKeepsDirtyMarkerForPendingDelta() {
        RedisPostService redisPostService = new RedisPostService(redisTemplate);
        stubStringRedisValueSerializer();
        doReturn(3L).when(redisTemplate).execute(
                any(RedisScript.class),
                any(RedisSerializer.class),
                any(RedisSerializer.class),
                any(List.class),
                any(Object.class),
                any(Object.class));

        assertThat(redisPostService.acknowledgeViewCountSyncBatch(42L, 12L)).isTrue();

        ArgumentCaptor<RedisScript> scriptCaptor = ArgumentCaptor.forClass(RedisScript.class);
        verify(redisTemplate).execute(
                scriptCaptor.capture(),
                any(RedisSerializer.class),
                any(RedisSerializer.class),
                eq(List.of(
                        "post:views:sync:inflight:42",
                        "post:views:42",
                        "posts:dirty:views",
                        "post:views:sync:sequence:42")),
                eq("12".getBytes(StandardCharsets.UTF_8)),
                eq("42".getBytes(StandardCharsets.UTF_8)));

        String script = scriptCaptor.getValue().getScriptAsString();
        assertThat(script).contains(
                "string.sub(currentBatch, 1, separator - 1) ~= ARGV[1]",
                "redis.call('DEL', KEYS[4])",
                "redis.call('SADD', KEYS[3], ARGV[2])",
                "redis.call('SREM', KEYS[3], ARGV[2])");
    }

    @Test
    @DisplayName("조회수 dirty 표식은 in-flight 또는 pending 작업이 없을 때만 제거한다")
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void clearDirtyPostIfNoSyncWorkChecksBothBatchAndPendingCounter() {
        RedisPostService redisPostService = new RedisPostService(redisTemplate);
        stubStringRedisValueSerializer();
        doReturn(0L).when(redisTemplate).execute(
                any(RedisScript.class),
                any(RedisSerializer.class),
                any(RedisSerializer.class),
                any(List.class),
                any(Object.class));

        redisPostService.clearDirtyPostIfNoSyncWork(42L);

        ArgumentCaptor<RedisScript> scriptCaptor = ArgumentCaptor.forClass(RedisScript.class);
        verify(redisTemplate).execute(
                scriptCaptor.capture(),
                any(RedisSerializer.class),
                any(RedisSerializer.class),
                eq(List.of(
                        "post:views:42",
                        "post:views:sync:inflight:42",
                        "posts:dirty:views")),
                eq("42".getBytes(StandardCharsets.UTF_8)));

        assertThat(scriptCaptor.getValue().getScriptAsString()).contains(
                "redis.call('GET', KEYS[2])",
                "redis.call('GET', KEYS[1])",
                "redis.call('SREM', KEYS[3], ARGV[1])");
    }

    private void stubStringRedisValueSerializer() {
        doReturn(valueSerializer).when(redisTemplate).getValueSerializer();
        when(valueSerializer.serialize(any())).thenAnswer(invocation ->
                invocation.getArgument(0, String.class).getBytes(StandardCharsets.UTF_8));
    }
}
