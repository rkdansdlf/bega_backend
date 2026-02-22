package com.example.bega.auth.service;

import com.example.bega.auth.dto.OAuth2LinkStateData;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class OAuth2LinkStateServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private ObjectMapper objectMapper;
    private OAuth2LinkStateService service;
    private Map<String, String> redisStorage;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        service = new OAuth2LinkStateService(redisTemplate, objectMapper);
        redisStorage = new ConcurrentHashMap<>();

        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        // 연동 state는 메모리 기반 Redis 동작으로 시뮬레이션한다.
        lenient().doAnswer(inv -> {
            String key = inv.getArgument(0);
            String value = inv.getArgument(1);
            redisStorage.put(key, value);
            return null;
        }).when(valueOperations).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));

        lenient().when(valueOperations.getAndDelete(anyString())).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            return redisStorage.remove(key);
        });
    }

    @Test
    @DisplayName("saveLinkByState: 원본 state key로 5분 TTL 저장")
    void saveLinkByState_storesWithFiveMinuteTtl() throws Exception {
        String state = "raw-state-1";
        OAuth2LinkStateData data = new OAuth2LinkStateData(
                "link",
                101L,
                "http://localhost:5173/mypage",
                System.currentTimeMillis(),
                null);

        service.saveLinkByState(state, data);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(keyCaptor.capture(), jsonCaptor.capture(), eq(5L), eq(TimeUnit.MINUTES));

        assertThat(keyCaptor.getValue()).isEqualTo("oauth2:link:" + state);
        OAuth2LinkStateData saved = objectMapper.readValue(jsonCaptor.getValue(), OAuth2LinkStateData.class);
        assertThat(saved).isEqualTo(data);
    }

    @Test
    @DisplayName("saveLinkByState: 직렬화 실패 시 RuntimeException")
    void saveLinkByState_throwsWhenSerializationFails() throws Exception {
        ObjectMapper failingMapper = mock(ObjectMapper.class);
        OAuth2LinkStateService failingService = new OAuth2LinkStateService(redisTemplate, failingMapper);
        when(failingMapper.writeValueAsString(any()))
                .thenThrow(new JsonProcessingException("serialization failure") {
                });

        OAuth2LinkStateData data = new OAuth2LinkStateData("link", 1L, null, System.currentTimeMillis(), null);

        assertThatThrownBy(() -> failingService.saveLinkByState("state", data))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Failed to save OAuth2 link state");

        verify(valueOperations, never()).set(anyString(), anyString(), eq(5L), eq(TimeUnit.MINUTES));
    }

    @Test
    @DisplayName("consumeLinkByState: 공백 state면 null")
    void consumeLinkByState_returnsNullForBlankState() {
        assertThat(service.consumeLinkByState("")).isNull();
        assertThat(service.consumeLinkByState(null)).isNull();
        verify(valueOperations, never()).getAndDelete(anyString());
    }

    @Test
    @DisplayName("consumeLinkByState: Redis 값이 없으면 null")
    void consumeLinkByState_returnsNullWhenMissing() {
        when(valueOperations.getAndDelete("oauth2:link:missing")).thenReturn(null);

        OAuth2LinkStateData result = service.consumeLinkByState("missing");

        assertThat(result).isNull();
        verify(valueOperations).getAndDelete("oauth2:link:missing");
    }

    @Test
    @DisplayName("consumeLinkByState: 만료된 상태면 null")
    void consumeLinkByState_returnsNullForExpiredData() throws Exception {
        String state = "expired-state";
        OAuth2LinkStateData expired = new OAuth2LinkStateData(
                "link",
                7L,
                null,
                System.currentTimeMillis() - (6 * 60 * 1000L),
                null);
        when(valueOperations.getAndDelete("oauth2:link:" + state))
                .thenReturn(objectMapper.writeValueAsString(expired));

        OAuth2LinkStateData result = service.consumeLinkByState(state);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("consumeLinkByState: 정상 데이터면 반환")
    void consumeLinkByState_returnsDataForValidState() throws Exception {
        String state = "ok-state";
        OAuth2LinkStateData stored = new OAuth2LinkStateData(
                "link",
                33L,
                "http://localhost:5173/mypage",
                System.currentTimeMillis(),
                null);
        when(valueOperations.getAndDelete("oauth2:link:" + state))
                .thenReturn(objectMapper.writeValueAsString(stored));

        OAuth2LinkStateData result = service.consumeLinkByState(state);

        assertThat(result).isEqualTo(stored);
    }

    @Test
    @DisplayName("consumeLinkByState: 역직렬화 실패 시 null")
    void consumeLinkByState_returnsNullWhenDeserializationFails() {
        when(valueOperations.getAndDelete("oauth2:link:broken")).thenReturn("{not-json}");

        OAuth2LinkStateData result = service.consumeLinkByState("broken");

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("round-trip: 저장한 state는 동일한 링크 데이터로 소비된다")
    void saveAndConsumeLinkByState_roundTrip() {
        String state = "round-trip-state";
        OAuth2LinkStateData original = new OAuth2LinkStateData(
                "link",
                300L,
                "http://localhost:5173/mypage",
                System.currentTimeMillis(),
                null);

        service.saveLinkByState(state, original);
        OAuth2LinkStateData consumed = service.consumeLinkByState(state);

        assertThat(consumed).isEqualTo(original);
        assertThat(redisStorage).doesNotContainKey("oauth2:link:" + state);
    }

    @Test
    @DisplayName("동일 state는 소비 후 두 번째 조회시 null")
    void consumeLinkByState_isOneTimeUse() {
        String state = "one-time-state";
        OAuth2LinkStateData original = new OAuth2LinkStateData(
                "link",
                301L,
                null,
                System.currentTimeMillis(),
                null);

        service.saveLinkByState(state, original);
        OAuth2LinkStateData first = service.consumeLinkByState(state);
        OAuth2LinkStateData second = service.consumeLinkByState(state);

        assertThat(first).isEqualTo(original);
        assertThat(second).isNull();
    }
}
