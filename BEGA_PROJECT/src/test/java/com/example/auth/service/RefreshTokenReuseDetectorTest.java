package com.example.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

import com.example.auth.util.JWTUtil;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class RefreshTokenReuseDetectorTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    private RefreshTokenReuseDetector detector;

    @BeforeEach
    void setUp() {
        detector = new RefreshTokenReuseDetector(redisTemplate);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void markRotated_persistsOwnerAndExactSessionWithRefreshLifetime() {
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> value = ArgumentCaptor.forClass(String.class);

        detector.markRotated("old-refresh", 27L, "session-27", 60_000L);

        verify(valueOperations).set(key.capture(), value.capture(), any(Duration.class));
        assertThat(key.getValue()).isEqualTo("refresh:rotated:" + JWTUtil.hashKey("old-refresh"));
        assertThat(value.getValue()).startsWith("v1:27:").doesNotContain("old-refresh");
        assertThat(valueOperations).isNotNull();
    }

    @Test
    void findReuseMarker_readsSessionMappingAndLegacyOwnerOnlyMarker() {
        String sessionValue = "v1:27:" + java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("legacy-session".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        when(valueOperations.get("refresh:rotated:" + JWTUtil.hashKey("rotated"))).thenReturn(sessionValue);
        when(valueOperations.get("refresh:rotated:" + JWTUtil.hashKey("older-rotated"))).thenReturn("27");

        assertThat(detector.findReuseMarker("rotated"))
                .contains(new RefreshTokenReuseDetector.ReuseMarker(27L, "legacy-session"));
        assertThat(detector.findReuseMarker("older-rotated"))
                .contains(new RefreshTokenReuseDetector.ReuseMarker(27L, null));
    }

    @Test
    void markRotated_failsClosedWhenSessionMappingCannotBeStored() {
        doThrow(new IllegalStateException("redis unavailable"))
                .when(valueOperations).set(anyString(), anyString(), any(Duration.class));

        assertThatThrownBy(() -> detector.markRotated("old-refresh", 27L, "session-27", 60_000L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Refresh token session mapping is unavailable");
    }
}
