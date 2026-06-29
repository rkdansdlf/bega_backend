package com.example.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.auth.entity.RefreshToken;
import com.example.auth.repository.RefreshRepository;
import com.example.auth.util.JWTUtil;
import jakarta.servlet.http.Cookie;
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
        existingToken.setToken("existing-refresh-token");
        existingToken.setSessionId("session\r\nadmin=true");
        existingToken.setDeviceType("desktop");
        existingToken.setDeviceLabel("Desktop");
        existingToken.setBrowser("Chrome");
        existingToken.setOs("macOS");
        existingToken.setIp("127.0.0.1");
        request.setCookies(new Cookie("Refresh", "existing-refresh-token"));

        when(refreshRepository.findAllByToken("existing-refresh-token"))
                .thenReturn(List.of(existingToken));
        when(authSessionMetadataResolver.resolve(any(HttpServletRequest.class))).thenReturn(metadata);

        AuthSessionService.PreparedRefreshSession prepared = authSessionService.prepareRefreshSession(
                "user@example.com",
                request);

        assertThat(prepared.sessionId()).doesNotContain("\r", "\n");
        assertThat(prepared.sessionId()).isNotEqualTo("session\r\nadmin=true");
        assertThat(prepared.matchedToken()).isSameAs(existingToken);
        verify(refreshRepository, never()).findAllByEmailOrderByIdDesc(anyString());
    }

    @Test
    @DisplayName("refresh 쿠키가 없는 로그인은 이메일 기준 refresh token 전체 목록을 읽지 않는다")
    void prepareRefreshSession_withoutRefreshCookieSkipsEmailTokenHistoryLookup() {
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

        AuthSessionService.PreparedRefreshSession prepared = authSessionService.prepareRefreshSession(
                "user@example.com",
                request);

        assertThat(prepared.matchedToken()).isNull();
        assertThat(prepared.sessionId()).isNotBlank();
        verify(refreshRepository, never()).findAllByEmailOrderByIdDesc(anyString());
    }

    @Test
    @DisplayName("refresh 쿠키가 있는 로그인은 token 인덱스 조회로 현재 세션만 찾는다")
    void prepareRefreshSession_withRefreshCookieUsesTokenLookupInsteadOfEmailTokenHistory() {
        AuthSessionService authSessionService = new AuthSessionService(
                refreshRepository,
                jwtUtil,
                authSessionMetadataResolver,
                refreshTokenReuseDetector);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("Refresh", "cookie-refresh-token"));
        AuthSessionMetadataResolver.SessionMetadata metadata = new AuthSessionMetadataResolver.SessionMetadata(
                "desktop",
                "Desktop",
                "Chrome",
                "macOS",
                "127.0.0.1",
                LocalDateTime.now());
        RefreshToken existingToken = new RefreshToken();
        existingToken.setEmail("user@example.com");
        existingToken.setToken("cookie-refresh-token");
        existingToken.setSessionId("session-123");

        when(authSessionMetadataResolver.resolve(any(HttpServletRequest.class))).thenReturn(metadata);
        when(refreshRepository.findAllByToken("cookie-refresh-token")).thenReturn(List.of(existingToken));

        AuthSessionService.PreparedRefreshSession prepared = authSessionService.prepareRefreshSession(
                "user@example.com",
                request);

        assertThat(prepared.sessionId()).isEqualTo("session-123");
        assertThat(prepared.matchedToken()).isSameAs(existingToken);
        verify(refreshRepository).findAllByToken("cookie-refresh-token");
        verify(refreshRepository, never()).findAllByEmailOrderByIdDesc(anyString());
    }

    @Test
    @DisplayName("stale refresh 쿠키는 session id로 기존 세션을 다시 찾는다")
    void prepareRefreshSession_withStaleRefreshCookieMatchesBySessionId() {
        AuthSessionService authSessionService = new AuthSessionService(
                refreshRepository,
                jwtUtil,
                authSessionMetadataResolver,
                refreshTokenReuseDetector);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("Refresh", "stale-refresh-token"));
        AuthSessionMetadataResolver.SessionMetadata metadata = new AuthSessionMetadataResolver.SessionMetadata(
                "desktop",
                "Desktop",
                "Chrome",
                "macOS",
                "127.0.0.1",
                LocalDateTime.now());
        RefreshToken existingToken = new RefreshToken();
        existingToken.setEmail("user@example.com");
        existingToken.setToken("db-refresh-token");
        existingToken.setSessionId("session-123");

        when(authSessionMetadataResolver.resolve(any(HttpServletRequest.class))).thenReturn(metadata);
        when(refreshRepository.findAllByToken("stale-refresh-token")).thenReturn(List.of());
        when(jwtUtil.getSessionId("stale-refresh-token")).thenReturn("session-123");
        when(refreshRepository.findAllByEmailAndSessionId("user@example.com", "session-123"))
                .thenReturn(List.of(existingToken));

        AuthSessionService.PreparedRefreshSession prepared = authSessionService.prepareRefreshSession(
                "user@example.com",
                request);

        assertThat(prepared.sessionId()).isEqualTo("session-123");
        assertThat(prepared.matchedToken()).isSameAs(existingToken);
    }

    @Test
    @DisplayName("refresh 쿠키가 없어도 같은 기기 메타데이터의 활성 세션을 재사용한다")
    void prepareRefreshSession_withoutRefreshCookieMatchesActiveSessionContext() {
        AuthSessionService authSessionService = new AuthSessionService(
                refreshRepository,
                jwtUtil,
                authSessionMetadataResolver,
                refreshTokenReuseDetector);
        MockHttpServletRequest request = new MockHttpServletRequest();
        LocalDateTime now = LocalDateTime.now();
        AuthSessionMetadataResolver.SessionMetadata metadata = new AuthSessionMetadataResolver.SessionMetadata(
                "desktop",
                "Desktop",
                "Chrome",
                "macOS",
                "127.0.0.1",
                now);
        RefreshToken existingToken = new RefreshToken();
        existingToken.setEmail("user@example.com");
        existingToken.setToken("db-refresh-token");
        existingToken.setSessionId("session-123");
        existingToken.setDeviceType("desktop");
        existingToken.setDeviceLabel("Desktop");
        existingToken.setBrowser("Chrome");
        existingToken.setOs("macOS");
        existingToken.setIp("127.0.0.1");
        existingToken.setExpiryDate(now.plusDays(7));

        when(authSessionMetadataResolver.resolve(any(HttpServletRequest.class))).thenReturn(metadata);
        when(refreshRepository.findActiveByEmailAndSessionContextOrderByLastSeenDesc(
                "user@example.com",
                "desktop",
                "Desktop",
                "Chrome",
                "macOS",
                "127.0.0.1",
                now)).thenReturn(List.of(existingToken));

        AuthSessionService.PreparedRefreshSession prepared = authSessionService.prepareRefreshSession(
                "user@example.com",
                request);

        assertThat(prepared.sessionId()).isEqualTo("session-123");
        assertThat(prepared.matchedToken()).isSameAs(existingToken);
    }

    @Test
    @DisplayName("다른 이메일의 session id나 기기 메타데이터는 재사용하지 않는다")
    void prepareRefreshSession_doesNotReuseOtherUsersSession() {
        AuthSessionService authSessionService = new AuthSessionService(
                refreshRepository,
                jwtUtil,
                authSessionMetadataResolver,
                refreshTokenReuseDetector);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("Refresh", "stale-refresh-token"));
        LocalDateTime now = LocalDateTime.now();
        AuthSessionMetadataResolver.SessionMetadata metadata = new AuthSessionMetadataResolver.SessionMetadata(
                "desktop",
                "Desktop",
                "Chrome",
                "macOS",
                "127.0.0.1",
                now);

        when(authSessionMetadataResolver.resolve(any(HttpServletRequest.class))).thenReturn(metadata);
        when(refreshRepository.findAllByToken("stale-refresh-token")).thenReturn(List.of());
        when(jwtUtil.getSessionId("stale-refresh-token")).thenReturn("session-123");
        when(refreshRepository.findAllByEmailAndSessionId("user@example.com", "session-123"))
                .thenReturn(List.of());
        when(refreshRepository.findActiveByEmailAndSessionContextOrderByLastSeenDesc(
                "user@example.com",
                "desktop",
                "Desktop",
                "Chrome",
                "macOS",
                "127.0.0.1",
                now)).thenReturn(List.of());

        AuthSessionService.PreparedRefreshSession prepared = authSessionService.prepareRefreshSession(
                "user@example.com",
                request);

        assertThat(prepared.matchedToken()).isNull();
        assertThat(prepared.sessionId()).isNotBlank();
        assertThat(prepared.sessionId()).isNotEqualTo("session-123");
    }

    @Test
    @DisplayName("같은 기기 메타데이터 중복 세션은 선택된 세션만 남기고 정리한다")
    void issueRefreshSession_deletesDuplicateSessionContextTokens() {
        AuthSessionService authSessionService = new AuthSessionService(
                refreshRepository,
                jwtUtil,
                authSessionMetadataResolver,
                refreshTokenReuseDetector);
        MockHttpServletRequest request = new MockHttpServletRequest();
        LocalDateTime now = LocalDateTime.now();
        AuthSessionMetadataResolver.SessionMetadata metadata = new AuthSessionMetadataResolver.SessionMetadata(
                "desktop",
                "Desktop",
                "Chrome",
                "macOS",
                "127.0.0.1",
                now);
        RefreshToken selectedToken = new RefreshToken();
        selectedToken.setId(10L);
        selectedToken.setEmail("user@example.com");
        selectedToken.setToken("old-refresh-token");
        selectedToken.setSessionId("session-selected");
        selectedToken.setDeviceType("desktop");
        selectedToken.setDeviceLabel("Desktop");
        selectedToken.setBrowser("Chrome");
        selectedToken.setOs("macOS");
        selectedToken.setIp("127.0.0.1");
        selectedToken.setExpiryDate(now.plusDays(7));
        RefreshToken duplicateToken = new RefreshToken();
        duplicateToken.setId(9L);
        duplicateToken.setEmail("user@example.com");
        duplicateToken.setToken("duplicate-refresh-token");
        duplicateToken.setSessionId("session-duplicate");
        duplicateToken.setDeviceType("desktop");
        duplicateToken.setDeviceLabel("Desktop");
        duplicateToken.setBrowser("Chrome");
        duplicateToken.setOs("macOS");
        duplicateToken.setIp("127.0.0.1");
        duplicateToken.setExpiryDate(now.plusDays(7));

        when(authSessionMetadataResolver.resolve(any(HttpServletRequest.class))).thenReturn(metadata);
        when(refreshRepository.findActiveByEmailAndSessionContextOrderByLastSeenDesc(
                "user@example.com",
                "desktop",
                "Desktop",
                "Chrome",
                "macOS",
                "127.0.0.1",
                now)).thenReturn(List.of(selectedToken, duplicateToken));
        when(jwtUtil.getRefreshTokenExpirationTime()).thenReturn(604_800_000L);
        when(jwtUtil.createRefreshToken("user@example.com", "ROLE_USER", 7L, 0, "session-selected"))
                .thenReturn("new-refresh-token");
        when(refreshRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(refreshRepository.findDuplicateSessionContexts(
                "user@example.com",
                "desktop",
                "Desktop",
                "Chrome",
                "macOS",
                "127.0.0.1",
                10L)).thenReturn(List.of(duplicateToken));

        AuthSessionService.IssuedRefreshSession issued = authSessionService.issueRefreshSession(
                "user@example.com",
                "ROLE_USER",
                7L,
                0,
                request);

        assertThat(issued.sessionId()).isEqualTo("session-selected");
        assertThat(selectedToken.getToken()).isEqualTo("new-refresh-token");
        verify(refreshRepository).deleteAll(org.mockito.ArgumentMatchers.argThat(tokens -> {
            if (!(tokens instanceof List<?> tokenList) || tokenList.size() != 1) {
                return false;
            }
            return tokenList.get(0) == duplicateToken;
        }));
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
