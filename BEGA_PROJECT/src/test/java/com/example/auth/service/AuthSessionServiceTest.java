package com.example.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.example.auth.entity.RefreshToken;
import com.example.auth.repository.RefreshRepository;
import com.example.auth.util.JWTUtil;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

@ExtendWith(MockitoExtension.class)
class AuthSessionServiceTest {

    @Mock
    private RefreshRepository refreshRepository;

    @Mock
    private JWTUtil jwtUtil;

    @Mock
    private AuthSessionMetadataResolver authSessionMetadataResolver;

    @Mock
    private RefreshTokenReuseDetector refreshTokenReuseDetector;

    @Test
    @DisplayName("prepareRefreshSession은 기존 refresh session id가 안전하지 않으면 새 id를 발급한다")
    void prepareRefreshSession_replacesUnsafeMatchedSessionId() {
        AuthSessionService authSessionService = new AuthSessionService(
                refreshRepository,
                jwtUtil,
                authSessionMetadataResolver,
                refreshTokenReuseDetector);
        MockHttpServletRequest request = new MockHttpServletRequest();
        AuthSessionMetadataResolver.SessionMetadata metadata = new AuthSessionMetadataResolver.SessionMetadata(
                "desktop",
                "Desktop",
                "Chrome",
                "macOS",
                "127.0.0.1",
                LocalDateTime.now());
        RefreshToken existingToken = new RefreshToken();
        existingToken.setEmail("user@example.com");
        existingToken.setSessionId("session\r\nadmin=true");
        existingToken.setDeviceType("desktop");
        existingToken.setDeviceLabel("Desktop");
        existingToken.setBrowser("Chrome");
        existingToken.setOs("macOS");
        existingToken.setIp("127.0.0.1");

        when(refreshRepository.findAllByEmailOrderByIdDesc("user@example.com"))
                .thenReturn(List.of(existingToken));
        when(authSessionMetadataResolver.resolve(any(HttpServletRequest.class))).thenReturn(metadata);
        when(authSessionMetadataResolver.normalizeText(anyString(), anyString()))
                .thenAnswer(invocation -> {
                    String value = invocation.getArgument(0);
                    String fallback = invocation.getArgument(1);
                    return value == null || value.isBlank() ? fallback : value.trim();
                });

        AuthSessionService.PreparedRefreshSession prepared = authSessionService.prepareRefreshSession(
                "user@example.com",
                request);

        assertThat(prepared.sessionId()).doesNotContain("\r", "\n");
        assertThat(prepared.sessionId()).isNotEqualTo("session\r\nadmin=true");
        assertThat(prepared.matchedToken()).isSameAs(existingToken);
    }

    @Test
    @DisplayName("refresh session id에 CRLF가 포함되면 새 안전한 session id를 발급한다")
    void rotateRefreshSession_replacesCrlfSessionId() {
        AuthSessionService authSessionService = new AuthSessionService(
                refreshRepository,
                jwtUtil,
                authSessionMetadataResolver,
                refreshTokenReuseDetector);
        MockHttpServletRequest request = new MockHttpServletRequest();
        AuthSessionMetadataResolver.SessionMetadata metadata = new AuthSessionMetadataResolver.SessionMetadata(
                "desktop",
                "Desktop",
                "Chrome",
                "macOS",
                "127.0.0.1",
                LocalDateTime.now());
        ArgumentCaptor<String> sessionIdCaptor = ArgumentCaptor.forClass(String.class);

        when(authSessionMetadataResolver.resolve(any(HttpServletRequest.class))).thenReturn(metadata);
        when(jwtUtil.getRefreshTokenExpirationTime()).thenReturn(604_800_000L);
        when(jwtUtil.createRefreshToken(
                eq("user@example.com"),
                eq("ROLE_USER"),
                eq(7L),
                eq(0),
                sessionIdCaptor.capture()))
                .thenReturn("new-refresh-token");
        when(refreshRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuthSessionService.IssuedRefreshSession issued = authSessionService.rotateRefreshSession(
                null,
                "user@example.com",
                "ROLE_USER",
                7L,
                0,
                request,
                "session\r\nadmin=true");

        assertThat(sessionIdCaptor.getValue()).doesNotContain("\r", "\n");
        assertThat(sessionIdCaptor.getValue()).isNotEqualTo("session\r\nadmin=true");
        assertThat(issued.sessionId()).isEqualTo(sessionIdCaptor.getValue());
    }

    @Test
    @DisplayName("안전한 refresh session id는 그대로 유지한다")
    void rotateRefreshSession_keepsSafeSessionId() {
        AuthSessionService authSessionService = new AuthSessionService(
                refreshRepository,
                jwtUtil,
                authSessionMetadataResolver,
                refreshTokenReuseDetector);
        MockHttpServletRequest request = new MockHttpServletRequest();
        AuthSessionMetadataResolver.SessionMetadata metadata = new AuthSessionMetadataResolver.SessionMetadata(
                "desktop",
                "Desktop",
                "Chrome",
                "macOS",
                "127.0.0.1",
                LocalDateTime.now());

        when(authSessionMetadataResolver.resolve(any(HttpServletRequest.class))).thenReturn(metadata);
        when(jwtUtil.getRefreshTokenExpirationTime()).thenReturn(604_800_000L);
        when(jwtUtil.createRefreshToken(anyString(), anyString(), eq(7L), eq(0), eq("session-123")))
                .thenReturn("new-refresh-token");
        when(refreshRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuthSessionService.IssuedRefreshSession issued = authSessionService.rotateRefreshSession(
                null,
                "user@example.com",
                "ROLE_USER",
                7L,
                0,
                request,
                " session-123 ");

        assertThat(issued.sessionId()).isEqualTo("session-123");
    }
}
