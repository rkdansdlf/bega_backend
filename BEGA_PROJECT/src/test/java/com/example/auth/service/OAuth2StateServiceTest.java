package com.example.auth.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.auth.dto.OAuth2StateData;
import com.example.auth.dto.OAuth2StateStorageData;
import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserRepository;
import com.example.kbo.entity.TeamEntity;
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
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class OAuth2StateServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthSecurityMonitoringService securityMonitoringService;

    private ObjectMapper objectMapper;
    private OAuth2StateService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        service = new OAuth2StateService(redisTemplate, objectMapper, userRepository, securityMonitoringService);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("saveState: stateId 생성 후 userId를 5분 TTL로 저장")
    void saveState_storesUserIdWithFiveMinuteTtl() throws Exception {
        String stateId = service.saveState(77L);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(keyCaptor.capture(), jsonCaptor.capture(), eq(Duration.ofMinutes(5)));

        assertThat(stateId).isNotBlank();
        assertThat(keyCaptor.getValue()).isEqualTo("oauth2:state:" + stateId);

        OAuth2StateStorageData stored = objectMapper.readValue(jsonCaptor.getValue(), OAuth2StateStorageData.class);
        assertThat(stored.userId()).isEqualTo(77L);
        assertThat(stored.createdAt()).isPositive();
    }

    @Test
    @DisplayName("saveState: 유효한 OAuth stateId를 로그에 남기지 않음")
    void saveState_doesNotLogUsableStateId() {
        Logger logger = (Logger) LoggerFactory.getLogger(OAuth2StateService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            String stateId = service.saveState(77L);

            assertThat(appender.list)
                    .extracting(ILoggingEvent::getFormattedMessage)
                    .noneMatch(message -> message.contains(stateId));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    @DisplayName("saveState: 직렬화 실패 시 RuntimeException")
    void saveState_throwsWhenSerializationFails() throws Exception {
        ObjectMapper failingMapper = mock(ObjectMapper.class);
        OAuth2StateService failingService = new OAuth2StateService(redisTemplate, failingMapper, userRepository,
                securityMonitoringService);
        when(failingMapper.writeValueAsString(any()))
                .thenThrow(new JsonProcessingException("serialization failure") {
                });

        assertThatThrownBy(() -> failingService.saveState(1L))
                .isInstanceOf(OAuth2StateService.OAuth2StateStoreException.class)
                .hasMessage(OAuth2StateService.ERROR_CODE_STATE_STORE_UNAVAILABLE);

        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("saveState: Redis 저장 실패 시 코드형 예외를 던진다")
    void saveState_throwsWhenRedisWriteFails() {
        doThrow(new RuntimeException("redis down"))
                .when(valueOperations)
                .set(anyString(), anyString(), any(Duration.class));

        assertThatThrownBy(() -> service.saveState(1L))
                .isInstanceOf(OAuth2StateService.OAuth2StateStoreException.class)
                .hasMessage(OAuth2StateService.ERROR_CODE_STATE_STORE_UNAVAILABLE);
    }

    @Test
    @DisplayName("saveState: Redis 예외 메시지에 포함된 state 키도 로그에 남기지 않음")
    void saveState_doesNotLogStateKeyFromRedisException() {
        String stateKeyMarker = "oauth2-state-key-error-sentinel";
        doThrow(new IllegalStateException("Redis failed for oauth2:state:" + stateKeyMarker))
                .when(valueOperations)
                .set(anyString(), anyString(), any(Duration.class));

        Logger logger = (Logger) LoggerFactory.getLogger(OAuth2StateService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            assertThatThrownBy(() -> service.saveState(77L))
                    .isInstanceOf(OAuth2StateService.OAuth2StateStoreException.class);

            assertThat(appender.list).allSatisfy(event -> {
                assertThat(event.getFormattedMessage()).doesNotContain(stateKeyMarker);
                for (var throwable = event.getThrowableProxy(); throwable != null;
                        throwable = throwable.getCause()) {
                    assertThat(throwable.getMessage()).doesNotContain(stateKeyMarker);
                }
            });
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    @DisplayName("consumeState: Redis 값이 없으면 null")
    void consumeState_returnsNullWhenMissing() {
        stubStateResult("missing", null);

        OAuth2StateData result = service.consumeState("missing");

        assertThat(result).isNull();
        verify(userRepository, never()).findById(any());
    }

    @Test
    @DisplayName("consumeState: 저장 데이터 만료 시 null")
    void consumeState_returnsNullWhenExpired() throws Exception {
        OAuth2StateStorageData expired = new OAuth2StateStorageData(10L, System.currentTimeMillis() - (6 * 60 * 1000L));
        stubStateResult("expired", "C:" + objectMapper.writeValueAsString(expired));

        OAuth2StateData result = service.consumeState("expired");

        assertThat(result).isNull();
        verify(userRepository, never()).findById(any());
    }

    @Test
    @DisplayName("consumeState: Redis 조회 실패 시 null")
    void consumeState_returnsNullWhenRedisReadFails() {
        when(redisTemplate.execute(any(), eq(List.of(
                "oauth2:state:redis-error", "oauth2:state-replay:redis-error")), anyString()))
                .thenThrow(new RuntimeException("redis down"));

        OAuth2StateData result = service.consumeState("redis-error");

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("consumeState: Redis 예외 메시지에 포함된 state ID를 로그에 남기지 않음")
    void consumeState_doesNotLogStateIdFromRedisException() {
        String stateId = "consume-state-error-sentinel";
        when(redisTemplate.execute(any(), eq(List.of(
                "oauth2:state:" + stateId, "oauth2:state-replay:" + stateId)), anyString()))
                .thenThrow(new IllegalStateException("Redis failed for oauth2:state:" + stateId));
        Logger logger = (Logger) LoggerFactory.getLogger(OAuth2StateService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            assertThat(service.consumeState(stateId)).isNull();
            assertNoLogContains(appender, stateId);
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    @DisplayName("consumeState: userId null이면 null")
    void consumeState_returnsNullWhenUserIdIsNull() throws Exception {
        OAuth2StateStorageData broken = new OAuth2StateStorageData(null, System.currentTimeMillis());
        stubStateResult("no-user", "C:" + objectMapper.writeValueAsString(broken));

        OAuth2StateData result = service.consumeState("no-user");

        assertThat(result).isNull();
        verify(userRepository, never()).findById(any());
    }

    @Test
    @DisplayName("consumeState: 사용자 조회 실패 시 null")
    void consumeState_returnsNullWhenUserNotFound() throws Exception {
        OAuth2StateStorageData storage = new OAuth2StateStorageData(404L, System.currentTimeMillis());
        stubStateResult("not-found", "C:" + objectMapper.writeValueAsString(storage));
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        OAuth2StateData result = service.consumeState("not-found");

        assertThat(result).isNull();
        verify(userRepository).findById(404L);
    }

    @Test
    @DisplayName("consumeState: 사용자 정보 매핑 성공 (favoriteTeam null -> 없음)")
    void consumeState_returnsMappedDataWithDefaultFavoriteTeam() throws Exception {
        OAuth2StateStorageData storage = new OAuth2StateStorageData(9L, System.currentTimeMillis());
        stubStateResult("ok", "C:" + objectMapper.writeValueAsString(storage));

        UserEntity user = new UserEntity();
        user.setId(9L);
        user.setEmail("user@test.com");
        user.setName("테스터");
        user.setRole("ROLE_USER");
        user.setProfileImageUrl("https://example.com/profile.png");
        user.setHandle("tester");
        when(userRepository.findById(9L)).thenReturn(Optional.of(user));

        OAuth2StateData result = service.consumeState("ok");

        assertThat(result).isNotNull();
        assertThat(result.email()).isEqualTo("user@test.com");
        assertThat(result.name()).isEqualTo("테스터");
        assertThat(result.role()).isEqualTo("ROLE_USER");
        assertThat(result.profileImageUrl()).isEqualTo("https://example.com/profile.png");
        assertThat(result.favoriteTeam()).isEqualTo("없음");
        assertThat(result.handle()).isEqualTo("tester");
    }

    @Test
    @DisplayName("consumeState: favoriteTeam이 있으면 teamId 반환")
    void consumeState_returnsFavoriteTeamIdWhenPresent() throws Exception {
        OAuth2StateStorageData storage = new OAuth2StateStorageData(10L, System.currentTimeMillis());
        stubStateResult("team", "C:" + objectMapper.writeValueAsString(storage));

        TeamEntity team = TeamEntity.builder()
                .teamId("LG")
                .teamName("LG 트윈스")
                .teamShortName("LG")
                .city("서울")
                .build();

        UserEntity user = new UserEntity();
        user.setId(10L);
        user.setEmail("team@test.com");
        user.setName("팀유저");
        user.setRole("ROLE_USER");
        user.setHandle("teamuser");
        user.setFavoriteTeam(team);

        when(userRepository.findById(10L)).thenReturn(Optional.of(user));

        OAuth2StateData result = service.consumeState("team");

        assertThat(result).isNotNull();
        assertThat(result.favoriteTeam()).isEqualTo("LG");
    }

    @Test
    @DisplayName("consumeState: 역직렬화 실패 시 null")
    void consumeState_returnsNullWhenDeserializationFails() {
        stubStateResult("broken", "C:{broken-json}");

        OAuth2StateData result = service.consumeState("broken");

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("consumeState: 응답 유실 재조회는 소유자에게만 60초 허용하고 userId만 저장")
    void consumeState_allowsShortLivedReplayOnlyForOwner() throws Exception {
        String stateId = "recoverable";
        String stateJson = objectMapper.writeValueAsString(
                new OAuth2StateStorageData(77L, System.currentTimeMillis()));
        UserEntity user = new UserEntity();
        user.setId(77L);
        user.setEmail("recover@test.com");
        user.setName("Recovery User");
        user.setRole("ROLE_USER");
        user.setHandle("recovery_user");
        List<String> keys = List.of("oauth2:state:" + stateId, "oauth2:state-replay:" + stateId);
        when(redisTemplate.execute(any(), eq(keys), anyString()))
                .thenReturn("C:" + stateJson)
                .thenReturn("R:" + stateJson);
        when(userRepository.findById(77L)).thenReturn(Optional.of(user));

        OAuth2StateData firstResponse = service.consumeState(stateId);
        OAuth2StateData recoveredResponse = service.consumeState(stateId, 77L);

        assertThat(firstResponse).isNotNull();
        assertThat(recoveredResponse).isEqualTo(firstResponse);
        ArgumentCaptor<RedisScript> scriptCaptor = ArgumentCaptor.forClass(RedisScript.class);
        ArgumentCaptor<String> ttlCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate, org.mockito.Mockito.times(2))
                .execute(scriptCaptor.capture(), eq(keys), ttlCaptor.capture());
        assertThat(ttlCaptor.getAllValues()).containsOnly("60");
        assertThat(scriptCaptor.getAllValues().getFirst().getScriptAsString())
                .contains("RENAME", "EXPIRE", "'C:'", "'R:'");
        assertThat(stateJson).doesNotContain("recover@test.com", "Recovery User");
    }

    @Test
    @DisplayName("consumeState: 재조회는 로그인하지 않았거나 다른 state 소유자면 거부")
    void consumeState_rejectsReplayForUnauthenticatedOrDifferentOwner() throws Exception {
        String stateJson = objectMapper.writeValueAsString(
                new OAuth2StateStorageData(77L, System.currentTimeMillis()));
        stubStateResult("replay-only", "R:" + stateJson);

        assertThat(service.consumeState("replay-only", null)).isNull();
        assertThat(service.consumeState("replay-only", 88L)).isNull();
        verify(userRepository, never()).findById(any());
    }

    @Test
    @DisplayName("consumeState: 공개 state 경로의 replay 접두 별칭으로 replay marker에 접근할 수 없음")
    void consumeState_rejectsReplayNamespaceAlias() {
        String markerStateId = "known-state";
        stubStateResult("replay:" + markerStateId, null);

        assertThat(service.consumeState("replay:" + markerStateId, 77L)).isNull();

        verify(redisTemplate).execute(
                any(),
                eq(List.of(
                        "oauth2:state:replay:" + markerStateId,
                        "oauth2:state-replay:replay:" + markerStateId)),
                anyString());
    }

    @Test
    @DisplayName("peekUserId: 정상 JSON이면 userId 반환")
    void peekUserId_returnsUserIdForValidState() throws Exception {
        when(valueOperations.get("oauth2:state:peek"))
                .thenReturn(objectMapper.writeValueAsString(new OAuth2StateStorageData(123L, System.currentTimeMillis())));

        Long userId = service.peekUserId("peek");

        assertThat(userId).isEqualTo(123L);
    }

    @Test
    @DisplayName("peekUserId: 값이 없거나 역직렬화 실패면 null")
    void peekUserId_returnsNullWhenMissingOrInvalid() {
        when(valueOperations.get("oauth2:state:missing")).thenReturn(null);
        when(valueOperations.get("oauth2:state:invalid")).thenReturn("{invalid-json}");

        assertThat(service.peekUserId("missing")).isNull();
        assertThat(service.peekUserId("invalid")).isNull();
    }

    @Test
    @DisplayName("peekUserId: Redis 조회 실패면 null")
    void peekUserId_returnsNullWhenRedisFails() {
        when(valueOperations.get("oauth2:state:redis-error"))
                .thenThrow(new RuntimeException("redis down"));

        assertThat(service.peekUserId("redis-error")).isNull();
    }

    @Test
    @DisplayName("peekUserId: Redis 예외 메시지에 포함된 state ID를 로그에 남기지 않음")
    void peekUserId_doesNotLogStateIdFromRedisException() {
        String stateId = "peek-state-error-sentinel";
        when(valueOperations.get("oauth2:state:" + stateId))
                .thenThrow(new IllegalStateException("Redis failed for oauth2:state:" + stateId));
        Logger logger = (Logger) LoggerFactory.getLogger(OAuth2StateService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            assertThat(service.peekUserId(stateId)).isNull();
            assertNoLogContains(appender, stateId);
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private void assertNoLogContains(ListAppender<ILoggingEvent> appender, String value) {
        assertThat(appender.list).allSatisfy(event -> {
            assertThat(event.getFormattedMessage()).doesNotContain(value);
            for (var throwable = event.getThrowableProxy(); throwable != null;
                    throwable = throwable.getCause()) {
                assertThat(throwable.getMessage()).doesNotContain(value);
            }
        });
    }

    private void stubStateResult(String stateId, String result) {
        when(redisTemplate.execute(
                any(),
                eq(List.of("oauth2:state:" + stateId, "oauth2:state-replay:" + stateId)),
                anyString()))
                .thenReturn(result);
    }
}
