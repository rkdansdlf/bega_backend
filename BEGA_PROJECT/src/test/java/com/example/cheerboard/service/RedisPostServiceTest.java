package com.example.cheerboard.service;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisPostServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

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
}
