package com.example.auth.controller;

import com.example.auth.entity.RefreshToken;
import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserRepository;
import com.example.auth.repository.RefreshRepository;
import com.example.auth.util.JWTUtil;
import com.example.common.dto.ApiResponse;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReissueControllerTokenTypeTest {

    @Mock
    private JWTUtil jwtUtil;

    @Mock
    private RefreshRepository refreshRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ReissueController reissueController;

    @Test
    @DisplayName("refresh 타입이 아닌 토큰은 재발급이 거부된다")
    void reissue_rejectsNonRefreshTokenType() {
        MockHttpServletRequest request = requestWithRefreshCookie("access-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtUtil.getTokenType("access-token")).thenReturn("access");

        ResponseEntity<?> result = reissueController.reissue(request, response);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(result.getBody()).isInstanceOf(ApiResponse.class);
        ApiResponse body = (ApiResponse) result.getBody();
        assertThat(body.isSuccess()).isFalse();
        assertThat(body.getMessage()).isEqualTo("유효하지 않은 Refresh Token 타입입니다.");

        verify(jwtUtil, never()).isExpired("access-token");
        verifyNoInteractions(refreshRepository);
    }

    @Test
    @DisplayName("토큰 파싱 오류면 재발급이 거부된다")
    void reissue_rejectsWhenTokenTypeParsingFails() {
        MockHttpServletRequest request = requestWithRefreshCookie("broken-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtUtil.getTokenType("broken-token")).thenThrow(new RuntimeException("invalid token"));

        ResponseEntity<?> result = reissueController.reissue(request, response);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(result.getBody()).isInstanceOf(ApiResponse.class);
        ApiResponse body = (ApiResponse) result.getBody();
        assertThat(body.isSuccess()).isFalse();
        assertThat(body.getMessage()).isEqualTo("유효하지 않은 Refresh Token입니다.");

        verifyNoInteractions(refreshRepository);
    }

    @Test
    @DisplayName("refresh 타입 토큰은 정상적으로 재발급된다")
    void reissue_allowsRefreshTokenType() {
        MockHttpServletRequest request = requestWithRefreshCookie("refresh-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        RefreshToken stored = new RefreshToken();
        stored.setEmail("user@test.com");
        stored.setToken("refresh-token");
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
        when(userRepository.findById(1L)).thenReturn(java.util.Optional.of(user));
        when(jwtUtil.createJwt("user@test.com", "ROLE_USER", 1L, 1000L * 60 * 60 * 2, 0))
                .thenReturn("new-access-token");
        when(jwtUtil.createRefreshToken("user@test.com", "ROLE_USER", 1L, 0))
                .thenReturn("new-refresh-token");
        when(jwtUtil.getRefreshTokenExpirationTime()).thenReturn(1000L * 60 * 60 * 24 * 7);

        ResponseEntity<?> result = reissueController.reissue(request, response);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isInstanceOf(ApiResponse.class);
        ApiResponse body = (ApiResponse) result.getBody();
        assertThat(body.isSuccess()).isTrue();

        verify(refreshRepository).save(stored);
        assertThat(response.getCookies()).extracting(Cookie::getName)
                .contains("Authorization", "Refresh");
    }

    private MockHttpServletRequest requestWithRefreshCookie(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("Refresh", token));
        return request;
    }
}
