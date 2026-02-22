package com.example.auth.controller;

import com.example.auth.dto.OAuth2StateData;
import com.example.auth.service.OAuth2StateService;
import com.example.auth.service.TokenBlacklistService;
import com.example.auth.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class APIControllerOAuth2StateFlowTest {

    @Mock
    private UserService userService;

    @Mock
    private OAuth2StateService oAuth2StateService;

    @Mock
    private TokenBlacklistService tokenBlacklistService;

    @InjectMocks
    private APIController apiController;

    @Test
    @DisplayName("state가 없거나 만료된 경우 404를 반환한다")
    void consumeOAuth2State_returns404WhenStateMissingOrExpired() {
        when(oAuth2StateService.consumeState("expired-state")).thenReturn(null);

        ResponseEntity<?> response = apiController.consumeOAuth2State("expired-state");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isInstanceOf(Map.class);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertThat(body.get("error")).isEqualTo("State not found or already consumed");
    }

    @Test
    @DisplayName("state는 one-time 소비되어 같은 state 재시도 시 404가 된다")
    void consumeOAuth2State_isOneTimeUseForSameState() {
        OAuth2StateData data = new OAuth2StateData(
                "user@test.com",
                "Tester",
                "ROLE_USER",
                null,
                "없음",
                "tester"
        );
        when(oAuth2StateService.consumeState("same-state"))
                .thenReturn(data)
                .thenReturn(null);

        ResponseEntity<?> first = apiController.consumeOAuth2State("same-state");
        ResponseEntity<?> second = apiController.consumeOAuth2State("same-state");

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(first.getBody()).isEqualTo(data);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(second.getBody()).isInstanceOf(Map.class);
        Map<?, ?> body = (Map<?, ?>) second.getBody();
        assertThat(body.get("error")).isEqualTo("State not found or already consumed");
    }

    @Test
    @DisplayName("만료 state 실패 후 새 state로 재시도하면 성공한다")
    void consumeOAuth2State_retryWithFreshStateSucceeds() {
        OAuth2StateData freshData = new OAuth2StateData(
                "retry@test.com",
                "RetryUser",
                "ROLE_USER",
                null,
                "LG",
                "retry_user"
        );

        when(oAuth2StateService.consumeState("stale-state")).thenReturn(null);
        when(oAuth2StateService.consumeState("fresh-state")).thenReturn(freshData);

        ResponseEntity<?> stale = apiController.consumeOAuth2State("stale-state");
        ResponseEntity<?> fresh = apiController.consumeOAuth2State("fresh-state");

        assertThat(stale.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(fresh.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fresh.getBody()).isEqualTo(freshData);
    }
}
