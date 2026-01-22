package com.example.cheerboard.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisPostService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String VIEW_COUNT_KEY = "post:views:%d";
    private static final String VIEWED_USERS_KEY = "post:viewed:%d";
    private static final String DIRTY_POSTS_KEY = "posts:dirty:views";
    private static final String HOT_STATUS_KEY = "post:hot:%d";
    private static final String HOT_POSTS_ZSET_KEY = "posts:hot:list";

    /**
     * 조회수 증가 로직 (Redis에서 관리)
     * 
     * @param postId 게시글 ID
     * @param userId 사용자 ID (null일 경우 익명)
     * @return 증가 후 조회수
     */
    public void incrementViewCount(Long postId, Long userId) {
        String viewedKey = String.format(VIEWED_USERS_KEY, postId);
        String identifier = (userId != null) ? userId.toString() : "anonymous";

        // 중복 조회 방지 (Set 활용)
        Boolean alreadyViewed = redisTemplate.opsForSet().isMember(Objects.requireNonNull(viewedKey),
                Objects.requireNonNull(identifier));
        if (Boolean.FALSE.equals(alreadyViewed)) {
            redisTemplate.opsForSet().add(Objects.requireNonNull(viewedKey), Objects.requireNonNull(identifier));
            // 하루 동안 유지 (중복 방지 세션)
            redisTemplate.expire(Objects.requireNonNull(viewedKey), Objects.requireNonNull(Duration.ofDays(1)));

            String viewKey = String.format(VIEW_COUNT_KEY, postId);
            redisTemplate.opsForValue().increment(Objects.requireNonNull(viewKey));

            // 동기화 대상 목록에 추가
            redisTemplate.opsForSet().add(DIRTY_POSTS_KEY, Objects.requireNonNull(postId.toString()));
        }
    }

    /**
     * Redis에 저장된 현재 조회수 조회
     * Redis에 없으면 DB 값을 기준으로 해야 하므로 null 반환 가능
     */
    public Integer getViewCount(Long postId) {
        Object val = redisTemplate.opsForValue().get(Objects.requireNonNull(String.format(VIEW_COUNT_KEY, postId)));
        return (val instanceof Integer) ? (Integer) val
                : (val instanceof Long) ? (Objects.requireNonNull((Long) val)).intValue() : null;
    }

    /**
     * 동기화가 필요한 게시글 ID 목록 조회
     */
    public Set<Long> getDirtyPostIds() {
        Set<Object> members = redisTemplate.opsForSet().members(DIRTY_POSTS_KEY);
        if (members == null)
            return Set.of();
        return members.stream()
                .map(obj -> Long.parseLong(obj.toString()))
                .collect(Collectors.toSet());
    }

    /**
     * 동기화 완료 후 Redis 데이터 정리 및 델타 차감
     */
    public void clearDirtyPost(Long postId, int delta) {
        redisTemplate.opsForSet().remove(DIRTY_POSTS_KEY, postId.toString());
        // 반영된 만큼 차감
        String key = String.format(VIEW_COUNT_KEY, postId);
        redisTemplate.opsForValue().increment(Objects.requireNonNull(key), -delta);
        redisTemplate.expire(Objects.requireNonNull(key), Objects.requireNonNull(Duration.ofHours(6)));
    }

    /**
     * HOT 게시글 상태 캐싱
     */
    public void cacheHotStatus(Long postId, boolean isHot) {
        redisTemplate.opsForValue().set(Objects.requireNonNull(String.format(HOT_STATUS_KEY, postId)), isHot,
                Objects.requireNonNull(Duration.ofMinutes(10)));
    }

    /**
     * 캐시된 HOT 게시글 상태 조회
     */
    public Boolean getCachedHotStatus(Long postId) {
        Object val = redisTemplate.opsForValue().get(Objects.requireNonNull(String.format(HOT_STATUS_KEY, postId)));
        return val instanceof Boolean ? (Boolean) val : false;
    }

    /**
     * HOT 게시글 점수 업데이트 (Sorted Set)
     */
    public void updateHotScore(Long postId, double score) {
        redisTemplate.opsForZSet().add(Objects.requireNonNull(HOT_POSTS_ZSET_KEY),
                Objects.requireNonNull(postId.toString()), score);
    }

    /**
     * HOT 게시글 ID 목록 조회 (점수 높은 순)
     */
    public Set<Long> getHotPostIds(int start, int end) {
        Set<Object> ids = redisTemplate.opsForZSet().reverseRange(HOT_POSTS_ZSET_KEY, start, end);
        if (ids == null)
            return Set.of();
        return ids.stream()
                .map(obj -> Long.parseLong(obj.toString()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * HOT 목록에서 특정 게시글 제거
     */
    public void removeFromHotList(Long postId) {
        redisTemplate.opsForZSet().remove(Objects.requireNonNull(HOT_POSTS_ZSET_KEY),
                Objects.requireNonNull(postId.toString()));
    }

    /**
     * HOT 목록 상위 N개만 남기고 정리
     */
    public void pruneHotList(int limit) {
        Long size = redisTemplate.opsForZSet().size(HOT_POSTS_ZSET_KEY);
        if (size != null && size > limit) {
            // 점수가 낮은 순(0부터)으로 (전체크기 - 리밋 - 1) 만큼 제거
            redisTemplate.opsForZSet().removeRange(HOT_POSTS_ZSET_KEY, 0, size - limit - 1);
        }
    }
}
