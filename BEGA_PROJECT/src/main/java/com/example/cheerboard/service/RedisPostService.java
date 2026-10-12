package com.example.cheerboard.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.GenericToStringSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Locale;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisPostService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String VIEW_COUNT_KEY = "post:views:%d";
    private static final String VIEWED_USERS_KEY = "post:viewed:%d";
    private static final String VIEW_SYNC_INFLIGHT_KEY = "post:views:sync:inflight:%d";
    private static final String VIEW_SYNC_SEQUENCE_KEY = "post:views:sync:sequence:%d";
    private static final String DIRTY_POSTS_KEY = "posts:dirty:views";
    private static final String HOT_STATUS_KEY = "post:hot:%d";
    private static final String HOT_POSTS_ZSET_KEY = "posts:hot:list";
    private static final String HOT_POSTS_ZSET_KEY_TEMPLATE = "posts:hot:list:%s";
    private static final RedisSerializer<byte[]> BINARY_SCRIPT_ARGUMENT_SERIALIZER = RedisSerializer.byteArray();
    private static final RedisSerializer<Long> SCRIPT_RESULT_SERIALIZER = new GenericToStringSerializer<>(Long.class);
    private static final RedisSerializer<String> STRING_SCRIPT_RESULT_SERIALIZER = RedisSerializer.string();
    private static final DefaultRedisScript<Long> INCREMENT_VIEW_COUNT_SCRIPT = new DefaultRedisScript<>("""
            local added = redis.call('SADD', KEYS[1], ARGV[1])
            if added == 0 then
                return 0
            end
            redis.call('EXPIRE', KEYS[1], tonumber(ARGV[2]))
            redis.call('INCR', KEYS[2])
            redis.call('PERSIST', KEYS[2])
            redis.call('SADD', KEYS[3], ARGV[3])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<String> CLAIM_VIEW_COUNT_SYNC_BATCH_SCRIPT = new DefaultRedisScript<>("""
            local existingBatch = redis.call('GET', KEYS[2])
            if existingBatch then
                return existingBatch
            end

            local delta = tonumber(redis.call('GET', KEYS[1]) or '0')
            if not delta or delta <= 0 then
                return ''
            end

            local minimumSequence = tonumber(ARGV[1]) or 0
            local currentSequence = tonumber(redis.call('GET', KEYS[3]) or '0')
            if not currentSequence or currentSequence < minimumSequence then
                redis.call('SET', KEYS[3], ARGV[1])
            end

            local sequence = redis.call('INCR', KEYS[3])
            local batch = tostring(sequence) .. '|' .. tostring(delta)
            redis.call('SET', KEYS[2], batch)
            redis.call('DEL', KEYS[1])
            redis.call('SADD', KEYS[4], ARGV[2])
            return batch
            """, String.class);
    private static final DefaultRedisScript<Long> ACK_VIEW_COUNT_SYNC_BATCH_SCRIPT = new DefaultRedisScript<>("""
            local currentBatch = redis.call('GET', KEYS[1])
            if currentBatch then
                local separator = string.find(currentBatch, '|', 1, true)
                if not separator or string.sub(currentBatch, 1, separator - 1) ~= ARGV[1] then
                    return -1
                end
                redis.call('DEL', KEYS[1])
                redis.call('DEL', KEYS[4])
            end

            local pendingDelta = tonumber(redis.call('GET', KEYS[2]) or '0')
            if pendingDelta and pendingDelta > 0 then
                redis.call('SADD', KEYS[3], ARGV[2])
                return pendingDelta
            end

            redis.call('DEL', KEYS[2])
            redis.call('SREM', KEYS[3], ARGV[2])
            return 0
            """, Long.class);
    private static final DefaultRedisScript<Long> CLEAR_EMPTY_DIRTY_MARKER_SCRIPT = new DefaultRedisScript<>("""
            local currentBatch = redis.call('GET', KEYS[2])
            local pendingDelta = tonumber(redis.call('GET', KEYS[1]) or '0')
            if currentBatch or (pendingDelta and pendingDelta > 0) then
                redis.call('SADD', KEYS[3], ARGV[1])
                return 1
            end

            redis.call('DEL', KEYS[1])
            redis.call('SREM', KEYS[3], ARGV[1])
            return 0
            """, Long.class);

    /**
     * 조회수 증가 로직 (Redis에서 관리)
     * 
     * @param postId 게시글 ID
     * @param userId 사용자 ID (null일 경우 익명)
     * @return 증가 후 조회수
     */
    public void incrementViewCount(Long postId, Long userId) {
        try {
            String viewedKey = String.format(VIEWED_USERS_KEY, postId);
            String identifier = (userId != null) ? userId.toString() : "anonymous";
            String viewKey = String.format(VIEW_COUNT_KEY, postId);

            // Deduplication, counter increment, and dirty registration must be one Redis operation.
            redisTemplate.execute(
                    INCREMENT_VIEW_COUNT_SCRIPT,
                    BINARY_SCRIPT_ARGUMENT_SERIALIZER,
                    SCRIPT_RESULT_SERIALIZER,
                    List.of(viewedKey, viewKey, DIRTY_POSTS_KEY),
                    serializeRedisValue(identifier),
                    Long.toString(Duration.ofDays(1).toSeconds()).getBytes(StandardCharsets.UTF_8),
                    serializeRedisValue(postId.toString()));
        } catch (Exception e) {
            log.warn("Redis error in incrementViewCount: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private byte[] serializeRedisValue(Object value) {
        RedisSerializer<Object> valueSerializer = (RedisSerializer<Object>) Objects.requireNonNull(
                redisTemplate.getValueSerializer());
        return valueSerializer.serialize(value);
    }

    public Optional<ViewCountSyncBatch> claimViewCountSyncBatch(Long postId, long minimumSequence) {
        String postIdValue = Objects.requireNonNull(postId).toString();
        String batch = redisTemplate.execute(
                CLAIM_VIEW_COUNT_SYNC_BATCH_SCRIPT,
                BINARY_SCRIPT_ARGUMENT_SERIALIZER,
                STRING_SCRIPT_RESULT_SERIALIZER,
                List.of(
                        String.format(VIEW_COUNT_KEY, postId),
                        String.format(VIEW_SYNC_INFLIGHT_KEY, postId),
                        String.format(VIEW_SYNC_SEQUENCE_KEY, postId),
                        DIRTY_POSTS_KEY),
                Long.toString(minimumSequence).getBytes(StandardCharsets.UTF_8),
                serializeRedisValue(postIdValue));
        if (batch == null || batch.isEmpty()) {
            return Optional.empty();
        }

        int separator = batch.indexOf('|');
        if (separator <= 0 || separator == batch.length() - 1) {
            throw new IllegalStateException("Redis view sync batch is malformed");
        }
        long sequence = Long.parseLong(batch.substring(0, separator));
        int delta = Integer.parseInt(batch.substring(separator + 1));
        if (sequence <= 0 || delta <= 0) {
            throw new IllegalStateException("Redis view sync batch values must be positive");
        }
        return Optional.of(new ViewCountSyncBatch(sequence, delta));
    }

    public boolean acknowledgeViewCountSyncBatch(Long postId, long sequence) {
        String postIdValue = Objects.requireNonNull(postId).toString();
        Long result = redisTemplate.execute(
                ACK_VIEW_COUNT_SYNC_BATCH_SCRIPT,
                BINARY_SCRIPT_ARGUMENT_SERIALIZER,
                SCRIPT_RESULT_SERIALIZER,
                List.of(
                        String.format(VIEW_SYNC_INFLIGHT_KEY, postId),
                        String.format(VIEW_COUNT_KEY, postId),
                        DIRTY_POSTS_KEY,
                        String.format(VIEW_SYNC_SEQUENCE_KEY, postId)),
                Long.toString(sequence).getBytes(StandardCharsets.UTF_8),
                serializeRedisValue(postIdValue));
        return result != null && result >= 0;
    }

    public void clearDirtyPostIfNoSyncWork(Long postId) {
        String postIdValue = Objects.requireNonNull(postId).toString();
        redisTemplate.execute(
                CLEAR_EMPTY_DIRTY_MARKER_SCRIPT,
                BINARY_SCRIPT_ARGUMENT_SERIALIZER,
                SCRIPT_RESULT_SERIALIZER,
                List.of(
                        String.format(VIEW_COUNT_KEY, postId),
                        String.format(VIEW_SYNC_INFLIGHT_KEY, postId),
                        DIRTY_POSTS_KEY),
                serializeRedisValue(postIdValue));
    }

    public record ViewCountSyncBatch(long sequence, int delta) {
    }

    /**
     * Redis에 저장된 현재 조회수 조회
     * Redis에 없으면 DB 값을 기준으로 해야 하므로 null 반환 가능
     */
    public Integer getViewCount(Long postId) {
        try {
            Object val = redisTemplate.opsForValue().get(Objects.requireNonNull(String.format(VIEW_COUNT_KEY, postId)));
            return (val instanceof Integer) ? (Integer) val
                    : (val instanceof Long) ? (Objects.requireNonNull((Long) val)).intValue() : null;
        } catch (Exception e) {
            log.warn("Redis error in getViewCount: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Scheduler read that distinguishes a missing counter from a Redis read failure.
     */
    public Optional<Integer> getViewCountForSync(Long postId) {
        Object value = redisTemplate.opsForValue().get(
                Objects.requireNonNull(String.format(VIEW_COUNT_KEY, postId)));
        if (value == null) {
            return Optional.empty();
        }
        if (value instanceof Integer integerValue) {
            return Optional.of(integerValue);
        }
        if (value instanceof Long longValue) {
            return Optional.of(longValue.intValue());
        }
        throw new IllegalStateException("Redis view counter has an unexpected value type");
    }

    /**
     * 동기화가 필요한 게시글 ID 목록 조회
     */
    public Set<Long> getDirtyPostIds() {
        try {
            Set<Object> members = redisTemplate.opsForSet().members(DIRTY_POSTS_KEY);
            if (members == null)
                return Set.of();
            return members.stream()
                    .map(obj -> Long.parseLong(obj.toString()))
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            log.warn("Redis error in getDirtyPostIds: {}", e.getMessage());
            return Set.of();
        }
    }

    /**
     * HOT 게시글 상태 캐싱
     */
    public void cacheHotStatus(Long postId, boolean isHot) {
        try {
            cacheHotStatusForSync(postId, isHot);
        } catch (Exception e) {
            log.warn("Redis error in cacheHotStatus: {}", e.getMessage());
        }
    }

    public void cacheHotStatusForSync(Long postId, boolean isHot) {
        redisTemplate.opsForValue().set(Objects.requireNonNull(String.format(HOT_STATUS_KEY, postId)), isHot,
                Objects.requireNonNull(Duration.ofMinutes(10)));
    }

    /**
     * 캐시된 HOT 게시글 상태 조회
     */
    public Boolean getCachedHotStatus(Long postId) {
        try {
            Object val = redisTemplate.opsForValue().get(Objects.requireNonNull(String.format(HOT_STATUS_KEY, postId)));
            return val instanceof Boolean ? (Boolean) val : null;
        } catch (Exception e) {
            log.warn("Redis error in getCachedHotStatus: {}", e.getMessage());
            return null;
        }
    }

    /**
     * HOT 게시글 점수 업데이트 (Sorted Set)
     */
    public void updateHotScore(Long postId, double score) {
        updateHotScore(postId, score, PopularFeedAlgorithm.TIME_DECAY);
    }

    /**
     * HOT 게시글 점수 업데이트 (알고리즘별 Sorted Set)
     */
    public void updateHotScore(Long postId, double score, PopularFeedAlgorithm algorithm) {
        try {
            updateHotScoreForSync(postId, score, algorithm);
        } catch (Exception e) {
            log.warn("Redis error in updateHotScore: {}", e.getMessage());
        }
    }

    public void updateHotScoreForSync(Long postId, double score, PopularFeedAlgorithm algorithm) {
        String key = resolveHotListKey(algorithm);
        redisTemplate.opsForZSet().add(Objects.requireNonNull(key),
                Objects.requireNonNull(postId.toString()), score);
    }

    /**
     * HOT 게시글 ID 목록 조회 (점수 높은 순)
     */
    public Set<Long> getHotPostIds(int start, int end) {
        return getHotPostIds(start, end, PopularFeedAlgorithm.TIME_DECAY);
    }

    /**
     * HOT 게시글 ID 목록 조회 (알고리즘별, 점수 높은 순)
     */
    public Set<Long> getHotPostIds(int start, int end, PopularFeedAlgorithm algorithm) {
        try {
            String key = resolveHotListKey(algorithm);
            Set<Object> ids = redisTemplate.opsForZSet().reverseRange(Objects.requireNonNull(key), start, end);
            if (ids == null)
                return Set.of();
            return ids.stream()
                    .map(obj -> Long.parseLong(obj.toString()))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        } catch (Exception e) {
            log.warn("Redis error in getHotPostIds: {}", e.getMessage());
            return Set.of();
        }
    }

    /**
     * HOT 목록에서 특정 게시글 제거
     */
    public void removeFromHotList(Long postId) {
        try {
            removeFromHotListForSync(postId);
        } catch (Exception e) {
            log.warn("Redis error in removeFromHotList: {}", e.getMessage());
        }
    }

    public void removeFromHotListForSync(Long postId) {
        Object member = Objects.requireNonNull(postId.toString());
        redisTemplate.opsForZSet().remove(Objects.requireNonNull(HOT_POSTS_ZSET_KEY), member);
        redisTemplate.opsForZSet()
                .remove(Objects.requireNonNull(resolveHotListKey(PopularFeedAlgorithm.ENGAGEMENT_RATE)), member);
    }

    /**
     * 여러 게시글의 조회수를 한 번에 조회 (Redis MGET)
     */
    public Map<Long, Integer> getViewCounts(Collection<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            List<Long> idList = new ArrayList<>(postIds);
            List<String> keys = idList.stream()
                    .map(id -> String.format(VIEW_COUNT_KEY, id))
                    .toList();

            List<Object> values = redisTemplate.opsForValue().multiGet(Objects.requireNonNull(keys));

            Map<Long, Integer> result = new HashMap<>();
            if (values != null) {
                for (int i = 0; i < idList.size(); i++) {
                    Object val = values.get(i);
                    Integer viewCount = null;
                    if (val instanceof Integer) {
                        viewCount = (Integer) val;
                    } else if (val instanceof Long) {
                        viewCount = ((Long) val).intValue();
                    }
                    if (viewCount != null) {
                        result.put(idList.get(i), viewCount);
                    }
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("Redis error in getViewCounts: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * Feed summary enrichment에 필요한 Redis 값을 한 pipeline으로 조회한다.
     */
    public FeedRedisStats getFeedRedisStats(Collection<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return FeedRedisStats.empty();
        }
        try {
            List<Long> idList = new ArrayList<>(postIds);
            List<String> viewKeys = idList.stream()
                    .map(id -> String.format(VIEW_COUNT_KEY, id))
                    .toList();
            List<String> hotStatusKeys = idList.stream()
                    .map(id -> String.format(HOT_STATUS_KEY, id))
                    .toList();

            List<Object> pipelineResults = redisTemplate.executePipelined(new SessionCallback<>() {
                @Override
                @SuppressWarnings({ "rawtypes", "unchecked" })
                public Object execute(RedisOperations operations) {
                    operations.opsForValue().multiGet(viewKeys);
                    operations.opsForValue().multiGet(hotStatusKeys);
                    return null;
                }
            });
            if (pipelineResults == null || pipelineResults.size() < 2) {
                return FeedRedisStats.empty();
            }

            return new FeedRedisStats(
                    toIntegerMap(idList, resultList(pipelineResults.get(0))),
                    toBooleanMap(idList, resultList(pipelineResults.get(1))));
        } catch (Exception e) {
            log.warn("Redis error in getFeedRedisStats: {}", e.getMessage());
            return FeedRedisStats.empty();
        }
    }

    /**
     * 여러 게시글의 HOT 상태를 한 번에 조회 (Redis MGET)
     */
    public Map<Long, Boolean> getCachedHotStatuses(Collection<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            List<Long> idList = new ArrayList<>(postIds);
            List<String> keys = idList.stream()
                    .map(id -> String.format(HOT_STATUS_KEY, id))
                    .toList();

            List<Object> values = redisTemplate.opsForValue().multiGet(Objects.requireNonNull(keys));

            Map<Long, Boolean> result = new HashMap<>();
            if (values != null) {
                for (int i = 0; i < idList.size(); i++) {
                    Object val = values.get(i);
                    if (val instanceof Boolean) {
                        result.put(idList.get(i), (Boolean) val);
                    }
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("Redis error in getCachedHotStatuses: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private List<?> resultList(Object result) {
        return result instanceof List<?> values ? values : Collections.emptyList();
    }

    private Map<Long, Integer> toIntegerMap(List<Long> idList, List<?> values) {
        Map<Long, Integer> result = new HashMap<>();
        for (int i = 0; i < idList.size() && i < values.size(); i++) {
            Integer value = toInteger(values.get(i));
            if (value != null) {
                result.put(idList.get(i), value);
            }
        }
        return result;
    }

    private Map<Long, Boolean> toBooleanMap(List<Long> idList, List<?> values) {
        Map<Long, Boolean> result = new HashMap<>();
        for (int i = 0; i < idList.size() && i < values.size(); i++) {
            Object value = values.get(i);
            if (value instanceof Boolean boolValue) {
                result.put(idList.get(i), boolValue);
            }
        }
        return result;
    }

    private Integer toInteger(Object value) {
        if (value instanceof Integer intValue) {
            return intValue;
        }
        if (value instanceof Long longValue) {
            return longValue.intValue();
        }
        return null;
    }

    public record FeedRedisStats(Map<Long, Integer> viewCounts, Map<Long, Boolean> hotStatuses) {
        private static FeedRedisStats empty() {
            return new FeedRedisStats(Collections.emptyMap(), Collections.emptyMap());
        }
    }

    /**
     * HOT 목록 상위 N개만 남기고 정리
     */
    public void pruneHotList(int limit) {
        pruneHotList(limit, PopularFeedAlgorithm.TIME_DECAY);
        pruneHotList(limit, PopularFeedAlgorithm.ENGAGEMENT_RATE);
    }

    public void pruneHotList(int limit, PopularFeedAlgorithm algorithm) {
        try {
            String key = resolveHotListKey(algorithm);
            Long size = redisTemplate.opsForZSet().size(Objects.requireNonNull(key));
            if (size != null && size > limit) {
                // 점수가 낮은 순(0부터)으로 (전체크기 - 리밋 - 1) 만큼 제거
                redisTemplate.opsForZSet().removeRange(Objects.requireNonNull(key), 0, size - limit - 1);
            }
        } catch (Exception e) {
            log.warn("Redis error in pruneHotList: {}", e.getMessage());
        }
    }

    public long getHotListSize(PopularFeedAlgorithm algorithm) {
        try {
            String key = resolveHotListKey(algorithm);
            Long size = redisTemplate.opsForZSet().size(Objects.requireNonNull(key));
            return size != null ? size : 0L;
        } catch (Exception e) {
            log.warn("Redis error in getHotListSize: {}", e.getMessage());
            return 0L;
        }
    }

    private String resolveHotListKey(PopularFeedAlgorithm algorithm) {
        PopularFeedAlgorithm resolved = algorithm != null ? algorithm : PopularFeedAlgorithm.TIME_DECAY;
        if (resolved == PopularFeedAlgorithm.TIME_DECAY || resolved == PopularFeedAlgorithm.HYBRID) {
            return HOT_POSTS_ZSET_KEY;
        }
        return String.format(HOT_POSTS_ZSET_KEY_TEMPLATE, resolved.name().toLowerCase(Locale.ROOT));
    }
}
