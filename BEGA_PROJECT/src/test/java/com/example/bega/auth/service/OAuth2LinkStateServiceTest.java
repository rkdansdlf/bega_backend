package com.example.bega.auth.service;

import com.example.auth.service.AuthSecurityMonitoringService;
import com.example.bega.auth.dto.OAuth2LinkStateData;
import com.example.bega.auth.dto.OAuth2LinkTicketData;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OAuth2LinkStateServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private AuthSecurityMonitoringService securityMonitoringService;

    private ObjectMapper objectMapper;
    private OAuth2LinkStateService service;
    private Map<String, String> redisStorage;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        service = new OAuth2LinkStateService(redisTemplate, objectMapper, securityMonitoringService);
        redisStorage = new ConcurrentHashMap<>();

        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);

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
    @DisplayName("issueLinkToken: 5분 TTL의 one-time opaque ticket 저장")
    void issueLinkToken_storesOpaqueTicketWithTtl() throws Exception {
        String ticket = service.issueLinkToken(101L);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(keyCaptor.capture(), jsonCaptor.capture(), eq(300_000L), eq(TimeUnit.MILLISECONDS));

        assertThat(ticket).isNotBlank();
        assertThat(keyCaptor.getValue()).startsWith("oauth2:link:ticket:");

        OAuth2LinkTicketData stored = objectMapper.readValue(jsonCaptor.getValue(), OAuth2LinkTicketData.class);
        assertThat(stored.userId()).isEqualTo(101L);
        assertThat(stored.consumed()).isFalse();
    }

    @Test
    @DisplayName("consumeLinkToken: 최초 소비는 성공하고 이후 재사용은 replay로 차단")
    void consumeLinkToken_detectsReplay() {
        String ticket = service.issueLinkToken(300L);

        OAuth2LinkStateService.LinkTicketConsumeResult first = service.consumeLinkToken(ticket);
        OAuth2LinkStateService.LinkTicketConsumeResult second = service.consumeLinkToken(ticket);
        OAuth2LinkStateService.LinkTicketConsumeResult third = service.consumeLinkToken(ticket);

        assertThat(first.userId()).isEqualTo(300L);
        assertThat(first.failureReason()).isNull();
        assertThat(second.failureReason()).isEqualTo(OAuth2LinkStateService.FAILURE_REPLAYED_LINK_TOKEN);
        assertThat(third.failureReason()).isEqualTo(OAuth2LinkStateService.FAILURE_REPLAYED_LINK_TOKEN);
    }

    @Test
    @DisplayName("consumeLinkToken: 빈 토큰이면 실패 코드 반환")
    void consumeLinkToken_returnsFailureForBlankTicket() {
        OAuth2LinkStateService.LinkTicketConsumeResult result = service.consumeLinkToken("");

        assertThat(result.userId()).isNull();
        assertThat(result.failureReason()).isEqualTo(OAuth2LinkStateService.FAILURE_MISSING_LINK_TOKEN);
        verify(valueOperations, never()).getAndDelete(anyString());
    }

    @Test
    @DisplayName("consumeLinkToken: 만료된 티켓이면 session expired로 거부")
    void consumeLinkToken_returnsExpiredForExpiredTicket() throws Exception {
        String ticket = service.issueLinkToken(7L);
        String storedKey = redisStorage.keySet().iterator().next();
        redisStorage.put(storedKey, objectMapper.writeValueAsString(
                new OAuth2LinkTicketData(7L, System.currentTimeMillis() - (6 * 60 * 1000L), false)));

        OAuth2LinkStateService.LinkTicketConsumeResult result = service.consumeLinkToken(ticket);

        assertThat(result.userId()).isNull();
        assertThat(result.failureReason()).isEqualTo(OAuth2LinkStateService.FAILURE_EXPIRED_LINK_TOKEN);
    }

    @Test
    @DisplayName("saveLinkByState: state 키로 5분 TTL 저장")
    void saveLinkByState_storesWithFiveMinuteTtl() throws Exception {
        OAuth2LinkStateData data = new OAuth2LinkStateData(101L, System.currentTimeMillis(), null);

        service.saveLinkByState("raw-state-1", data);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(keyCaptor.capture(), jsonCaptor.capture(), eq(300_000L), eq(TimeUnit.MILLISECONDS));

        assertThat(keyCaptor.getAllValues()).anyMatch(value -> value.equals("oauth2:link:state:raw-state-1"));
        OAuth2LinkStateData saved = objectMapper.readValue(jsonCaptor.getAllValues().get(jsonCaptor.getAllValues().size() - 1),
                OAuth2LinkStateData.class);
        assertThat(saved).isEqualTo(data);
    }

    @Test
    @DisplayName("saveLinkByState: 직렬화 실패 시 코드형 예외")
    void saveLinkByState_throwsWhenSerializationFails() throws Exception {
        ObjectMapper failingMapper = mock(ObjectMapper.class);
        OAuth2LinkStateService failingService = new OAuth2LinkStateService(
                redisTemplate,
                failingMapper,
                securityMonitoringService);
        when(failingMapper.writeValueAsString(any()))
                .thenThrow(new JsonProcessingException("serialization failure") {
                });

        OAuth2LinkStateData data = new OAuth2LinkStateData(1L, System.currentTimeMillis(), null);

        assertThatThrownBy(() -> failingService.saveLinkByState("state", data))
                .isInstanceOf(OAuth2LinkStateService.OAuth2LinkStateStoreException.class)
                .hasMessage(OAuth2LinkStateService.ERROR_CODE_LINK_STATE_STORE_UNAVAILABLE);
    }

    @Test
    @DisplayName("issueLinkToken: Redis 저장 실패 시 코드형 예외")
    void issueLinkToken_throwsWhenRedisWriteFails() {
        doThrow(new RuntimeException("redis down"))
                .when(valueOperations)
                .set(anyString(), anyString(), anyLong(), any(TimeUnit.class));

        assertThatThrownBy(() -> service.issueLinkToken(1L))
                .isInstanceOf(OAuth2LinkStateService.OAuth2LinkStateStoreException.class)
                .hasMessage(OAuth2LinkStateService.ERROR_CODE_LINK_STATE_STORE_UNAVAILABLE);
    }

    @Test
    @DisplayName("consumeLinkByState: 정상 데이터면 한 번만 반환")
    void consumeLinkByState_isOneTimeUse() {
        OAuth2LinkStateData original = new OAuth2LinkStateData(301L, System.currentTimeMillis(), null);

        service.saveLinkByState("one-time-state", original);
        OAuth2LinkStateData first = service.consumeLinkByState("one-time-state");
        OAuth2LinkStateData second = service.consumeLinkByState("one-time-state");

        assertThat(first).isEqualTo(original);
        assertThat(second).isNull();
    }
}
