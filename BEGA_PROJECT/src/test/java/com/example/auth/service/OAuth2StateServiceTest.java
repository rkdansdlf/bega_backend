package com.example.auth.service;

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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
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
    @DisplayName("saveState: 직렬화 실패 시 RuntimeException")
    void saveState_throwsWhenSerializationFails() throws Exception {
        ObjectMapper failingMapper = mock(ObjectMapper.class);
        OAuth2StateService failingService = new OAuth2StateService(redisTemplate, failingMapper, userRepository,
                securityMonitoringService);
        when(failingMapper.writeValueAsString(any()))
                .thenThrow(new JsonProcessingException("serialization failure") {
                });

        assertThatThrownBy(() -> failingService.saveState(1L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Failed to serialize OAuth2 state data");

        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("consumeState: Redis 값이 없으면 null")
    void consumeState_returnsNullWhenMissing() {
        when(valueOperations.getAndDelete("oauth2:state:missing")).thenReturn(null);

        OAuth2StateData result = service.consumeState("missing");

        assertThat(result).isNull();
        verify(userRepository, never()).findById(any());
    }

    @Test
    @DisplayName("consumeState: 저장 데이터 만료 시 null")
    void consumeState_returnsNullWhenExpired() throws Exception {
        OAuth2StateStorageData expired = new OAuth2StateStorageData(10L, System.currentTimeMillis() - (6 * 60 * 1000L));
        when(valueOperations.getAndDelete("oauth2:state:expired"))
                .thenReturn(objectMapper.writeValueAsString(expired));

        OAuth2StateData result = service.consumeState("expired");

        assertThat(result).isNull();
        verify(userRepository, never()).findById(any());
    }

    @Test
    @DisplayName("consumeState: userId null이면 null")
    void consumeState_returnsNullWhenUserIdIsNull() throws Exception {
        OAuth2StateStorageData broken = new OAuth2StateStorageData(null, System.currentTimeMillis());
        when(valueOperations.getAndDelete("oauth2:state:no-user"))
                .thenReturn(objectMapper.writeValueAsString(broken));

        OAuth2StateData result = service.consumeState("no-user");

        assertThat(result).isNull();
        verify(userRepository, never()).findById(any());
    }

    @Test
    @DisplayName("consumeState: 사용자 조회 실패 시 null")
    void consumeState_returnsNullWhenUserNotFound() throws Exception {
        OAuth2StateStorageData storage = new OAuth2StateStorageData(404L, System.currentTimeMillis());
        when(valueOperations.getAndDelete("oauth2:state:not-found"))
                .thenReturn(objectMapper.writeValueAsString(storage));
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        OAuth2StateData result = service.consumeState("not-found");

        assertThat(result).isNull();
        verify(userRepository).findById(404L);
    }

    @Test
    @DisplayName("consumeState: 사용자 정보 매핑 성공 (favoriteTeam null -> 없음)")
    void consumeState_returnsMappedDataWithDefaultFavoriteTeam() throws Exception {
        OAuth2StateStorageData storage = new OAuth2StateStorageData(9L, System.currentTimeMillis());
        when(valueOperations.getAndDelete("oauth2:state:ok"))
                .thenReturn(objectMapper.writeValueAsString(storage));

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
        when(valueOperations.getAndDelete("oauth2:state:team"))
                .thenReturn(objectMapper.writeValueAsString(storage));

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
        when(valueOperations.getAndDelete("oauth2:state:broken")).thenReturn("{broken-json}");

        OAuth2StateData result = service.consumeState("broken");

        assertThat(result).isNull();
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
}
