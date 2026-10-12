package com.example.auth.controller;

import com.example.auth.dto.OAuth2StateData;
import com.example.auth.service.OAuth2StateService;
import com.example.auth.service.TokenBlacklistService;
import com.example.auth.service.UserService;
import com.example.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.HttpHeaders;

import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(apiController)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("state가 없거나 만료된 경우 404를 반환한다")
    void consumeOAuth2State_returns404WhenStateMissingOrExpired() throws Exception {
        when(oAuth2StateService.consumeState("expired-state", null)).thenReturn(null);

        mockMvc.perform(get("/api/auth/oauth2/state/expired-state"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("OAUTH2_STATE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("State not found or already consumed"));
    }

    @Test
    @DisplayName("state는 one-time 소비되어 같은 state 재시도 시 404가 된다")
    void consumeOAuth2State_isOneTimeUseForSameState() throws Exception {
        OAuth2StateData data = new OAuth2StateData(
                "user@test.com",
                "Tester",
                "ROLE_USER",
                null,
                "없음",
                "tester"
        );
        when(oAuth2StateService.consumeState("same-state", null))
                .thenReturn(data)
                .thenReturn(null);

        mockMvc.perform(get("/api/auth/oauth2/state/same-state"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("user@test.com"))
                .andExpect(jsonPath("$.name").value("Tester"))
                .andExpect(jsonPath("$.handle").value("tester"));

        mockMvc.perform(get("/api/auth/oauth2/state/same-state"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("OAUTH2_STATE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("State not found or already consumed"));
    }

    @Test
    @DisplayName("만료 state 실패 후 새 state로 재시도하면 성공한다")
    void consumeOAuth2State_retryWithFreshStateSucceeds() throws Exception {
        OAuth2StateData freshData = new OAuth2StateData(
                "retry@test.com",
                "RetryUser",
                "ROLE_USER",
                null,
                "LG",
                "retry_user"
        );

        when(oAuth2StateService.consumeState("stale-state", null)).thenReturn(null);
        when(oAuth2StateService.consumeState("fresh-state", null)).thenReturn(freshData);

        mockMvc.perform(get("/api/auth/oauth2/state/stale-state"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("OAUTH2_STATE_NOT_FOUND"));

        mockMvc.perform(get("/api/auth/oauth2/state/fresh-state"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("retry@test.com"))
                .andExpect(jsonPath("$.favoriteTeam").value("LG"))
                .andExpect(jsonPath("$.handle").value("retry_user"));
    }

    @Test
    @DisplayName("인증된 OAuth 콜백 재시도는 state 소유자 ID와 함께 전달한다")
    void consumeOAuth2State_passesAuthenticatedOwnerForReplay() throws Exception {
        OAuth2StateData data = new OAuth2StateData(
                "user@test.com",
                "Tester",
                "ROLE_USER",
                null,
                "없음",
                "tester");
        when(oAuth2StateService.consumeState("same-state", 77L)).thenReturn(data);
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(77L, "credentials"));

        try {
            mockMvc.perform(get("/api/auth/oauth2/state/same-state"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                    .andExpect(jsonPath("$.email").value("user@test.com"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
