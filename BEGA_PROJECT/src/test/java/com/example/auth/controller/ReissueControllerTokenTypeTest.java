package com.example.auth.controller;

import com.example.auth.entity.RefreshToken;
import com.example.auth.entity.UserEntity;
import com.example.auth.service.AuthSessionMetadataResolver;
import com.example.auth.service.AuthSessionService;
import com.example.auth.service.AuthSecurityMonitoringService;
import com.example.auth.util.AuthCookieUtil;
import com.example.auth.repository.UserRepository;
import com.example.auth.repository.RefreshRepository;
import com.example.auth.util.JWTUtil;
import com.example.common.exception.GlobalExceptionHandler;
import com.example.common.web.ClientIpResolver;
import com.example.common.dto.ApiResponse;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ReissueControllerTokenTypeTest {

    @Mock
    private JWTUtil jwtUtil;

    @Mock
    private RefreshRepository refreshRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ClientIpResolver clientIpResolver;

    @Mock
    private AuthSecurityMonitoringService authSecurityMonitoringService;

    private AuthSessionService authSessionService;
    private ReissueController reissueController;
    private MockMvc mockMvc;

    private final AuthCookieUtil authCookieUtil = new AuthCookieUtil(false);

    @BeforeEach
    void setUp() {
        authSessionService = new AuthSessionService(
                refreshRepository,
                jwtUtil,
                new AuthSessionMetadataResolver(clientIpResolver));
        reissueController = new ReissueController(
                jwtUtil,
                refreshRepository,
                userRepository,
                authCookieUtil,
                authSessionService,
                authSecurityMonitoringService);
        mockMvc = MockMvcBuilders.standaloneSetup(reissueController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("refresh 타입이 아닌 토큰은 재발급이 거부된다")
    void reissue_rejectsNonRefreshTokenType() throws Exception {
        when(jwtUtil.getTokenType("access-token")).thenReturn("access");

        mockMvc.perform(post("/api/auth/reissue")
                        .cookie(new Cookie("Refresh", "access-token")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN_TYPE"))
                .andExpect(jsonPath("$.message").value("유효하지 않은 Refresh Token 타입입니다."));

        verify(jwtUtil, never()).isExpired("access-token");
        verifyNoInteractions(refreshRepository);
        verify(authSecurityMonitoringService).recordRefreshReissueReject("INVALID_REFRESH_TOKEN_TYPE");
    }

    @Test
    @DisplayName("토큰 파싱 오류면 재발급이 거부된다")
    void reissue_rejectsWhenTokenTypeParsingFails() throws Exception {
        when(jwtUtil.getTokenType("broken-token")).thenThrow(new RuntimeException("invalid token"));

        mockMvc.perform(post("/api/auth/reissue")
                        .cookie(new Cookie("Refresh", "broken-token")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"))
                .andExpect(jsonPath("$.message").value("유효하지 않은 Refresh Token입니다."));

        verifyNoInteractions(refreshRepository);
        verify(authSecurityMonitoringService).recordRefreshReissueReject("INVALID_REFRESH_TOKEN");
    }

    @Test
    @DisplayName("refresh 타입 토큰은 정상적으로 재발급된다")
    void reissue_allowsRefreshTokenType() {
        MockHttpServletRequest request = requestWithRefreshCookie("refresh-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        RefreshToken stored = new RefreshToken();
        stored.setEmail("user@test.com");
        stored.setToken("refresh-token");
        stored.setSessionId("session-1");
        UserEntity user = UserEntity.builder()
                .id(1L)
                .enabled(true)
                .locked(false)
                .tokenVersion(0)
                .email("user@test.com")
                .build();

        when(jwtUtil.getTokenType("refresh-token")).thenReturn("refresh");
        when(jwtUtil.isExpired("refresh-token")).thenReturn(false);
        when(jwtUtil.getEmail("refresh-token")).thenReturn("user@test.com");
        when(refreshRepository.findAllByEmailOrderByIdDesc("user@test.com")).thenReturn(List.of(stored));
        when(jwtUtil.getRole("refresh-token")).thenReturn("ROLE_USER");
        when(jwtUtil.getUserId("refresh-token")).thenReturn(1L);
        when(jwtUtil.getTokenVersion("refresh-token")).thenReturn(0);
        when(jwtUtil.getAccessTokenExpirationTime()).thenReturn(1000L * 60 * 60 * 2);
        when(userRepository.findById(1L)).thenReturn(java.util.Optional.of(user));
        when(jwtUtil.createJwt("user@test.com", "ROLE_USER", 1L, 1000L * 60 * 60 * 2, 0))
                .thenReturn("new-access-token");
        when(jwtUtil.createRefreshToken("user@test.com", "ROLE_USER", 1L, 0, "session-1"))
                .thenReturn("new-refresh-token");
        when(jwtUtil.getRefreshTokenExpirationTime()).thenReturn(1000L * 60 * 60 * 24 * 7);
        when(clientIpResolver.resolveOrUnknown(request)).thenReturn("127.0.0.1");

        ResponseEntity<?> result = reissueController.reissue(request, response);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isInstanceOf(ApiResponse.class);
        ApiResponse body = (ApiResponse) result.getBody();
        assertThat(body.isSuccess()).isTrue();

        verify(refreshRepository).save(stored);
        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).anyMatch(header -> header.startsWith("Authorization="));
        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).anyMatch(header -> header.startsWith("Refresh="));
    }

    private MockHttpServletRequest requestWithRefreshCookie(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("Refresh", token));
        return request;
    }
}
