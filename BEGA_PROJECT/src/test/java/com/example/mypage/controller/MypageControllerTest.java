package com.example.mypage.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.auth.entity.RefreshToken;
import com.example.auth.entity.UserEntity;
import com.example.auth.repository.RefreshRepository;
import com.example.auth.service.AccountSecurityService;
import com.example.auth.service.AuthSessionMetadataResolver;
import com.example.auth.service.AuthSessionService;
import com.example.auth.service.PolicyConsentService;
import com.example.auth.service.UserService;
import com.example.auth.util.AuthCookieUtil;
import com.example.auth.util.JWTUtil;
import com.example.common.dto.ApiResponse;
import com.example.common.exception.ConflictBusinessException;
import com.example.common.web.ClientIpResolver;
import com.example.mypage.dto.ChangePasswordRequest;
import com.example.profile.storage.service.ProfileImageService;
import jakarta.servlet.http.Cookie;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

@ExtendWith(MockitoExtension.class)
class MypageControllerTest {

    private static final String MAC_CHROME_USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";
    private static final String WINDOWS_CHROME_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";

    @Mock
    private UserService userService;

    @Mock
    private JWTUtil jwtUtil;

    @Mock
    private ProfileImageService profileImageService;

    @Mock
    private RefreshRepository refreshRepository;

    @Mock
    private PolicyConsentService policyConsentService;

    @Mock
    private AccountSecurityService accountSecurityService;

    @Mock
    private ClientIpResolver clientIpResolver;

    private AuthSessionService authSessionService;
    private MypageController controller;

    @BeforeEach
    void setUp() {
        authSessionService = new AuthSessionService(
                refreshRepository,
                jwtUtil,
                new AuthSessionMetadataResolver(clientIpResolver));
        lenient().when(clientIpResolver.resolveOrUnknown(any())).thenAnswer(invocation -> {
            MockHttpServletRequest request = (MockHttpServletRequest) invocation.getArgument(0);
            return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
        });
        controller = new MypageController(
                userService,
                jwtUtil,
                profileImageService,
                new AuthCookieUtil(false),
                policyConsentService,
                accountSecurityService,
                authSessionService);
    }

    @Test
    void changePassword_expiresAuthCookiesAfterSuccess() {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("current-password");
        request.setNewPassword("NewPassword1!");
        request.setConfirmPassword("NewPassword1!");

        ResponseEntity<ApiResponse> response = controller.changePassword(1L, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE))
                .anyMatch(header -> header.startsWith("Authorization=") && header.contains("Max-Age=0"));
        assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE))
                .anyMatch(header -> header.startsWith("Refresh=") && header.contains("Max-Age=0"));
        verify(userService).changePassword(1L, "current-password", "NewPassword1!");
    }

    @Test
    void getSessions_marksCurrentSessionUsingStableSessionIdWhenRefreshCookieIsStale() throws Exception {
        UserEntity user = createUser(1L, "user@example.com");
        RefreshToken currentSession = createRefreshToken(
                101L,
                "session-current",
                user.getEmail(),
                "db-refresh-current",
                "desktop",
                "Mac",
                "Chrome",
                "macOS",
                "127.0.0.1");
        RefreshToken otherSession = createRefreshToken(
                100L,
                "session-other",
                user.getEmail(),
                "db-refresh-other",
                "mobile",
                "iPhone",
                "Safari",
                "iOS",
                "198.51.100.20");
        MockHttpServletRequest request = buildRequest(MAC_CHROME_USER_AGENT, "stale-cookie", "127.0.0.1");

        when(userService.findUserById(1L)).thenReturn(user);
        when(refreshRepository.findAllByEmailOrderByIdDesc(user.getEmail()))
                .thenReturn(List.of(otherSession, currentSession));
        when(jwtUtil.getSessionId("stale-cookie")).thenReturn("session-current");

        ResponseEntity<ApiResponse> response = controller.getSessions(1L, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).isInstanceOf(List.class);

        List<?> sessions = (List<?>) response.getBody().getData();
        assertThat(sessions).hasSize(2);
        assertThat(readBooleanProperty(sessions.get(0), "isCurrent")).isTrue();
        assertThat(readStringProperty(sessions.get(0), "getId")).isEqualTo("session-current");
    }

    @Test
    void deleteSessions_deletesOnlyOtherSessionsWhenCurrentResolvedByStableSessionId() {
        UserEntity user = createUser(1L, "user@example.com");
        RefreshToken currentSession = createRefreshToken(
                101L,
                "session-current",
                user.getEmail(),
                "db-refresh-current",
                "desktop",
                "Mac",
                "Chrome",
                "macOS",
                "127.0.0.1");
        RefreshToken otherSession = createRefreshToken(
                200L,
                "session-other",
                user.getEmail(),
                "db-refresh-other",
                "mobile",
                "iPhone",
                "Safari",
                "iOS",
                "198.51.100.20");
        MockHttpServletRequest request = buildRequest(MAC_CHROME_USER_AGENT, "stale-cookie", "127.0.0.1");

        when(userService.findUserById(1L)).thenReturn(user);
        when(refreshRepository.findAllByEmailOrderByIdDesc(user.getEmail()))
                .thenReturn(List.of(otherSession, currentSession));
        when(jwtUtil.getSessionId("stale-cookie")).thenReturn("session-current");

        ResponseEntity<ApiResponse> response = controller.deleteSessions(1L, true, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(refreshRepository).deleteAll(org.mockito.ArgumentMatchers.argThat(tokens -> {
            if (!(tokens instanceof List<?> tokenList) || tokenList.size() != 1) {
                return false;
            }
            Object first = tokenList.get(0);
            return first instanceof RefreshToken refreshToken && Long.valueOf(200L).equals(refreshToken.getId());
        }));
        verify(accountSecurityService).recordOtherSessionsRevoked(1L, 1);
    }

    @Test
    void deleteSessions_returnsConflictWhenCurrentSessionCannotBeResolved() {
        UserEntity user = createUser(1L, "user@example.com");
        RefreshToken macSession = createRefreshToken(
                101L,
                "session-current",
                user.getEmail(),
                "db-refresh-current",
                "desktop",
                "Mac",
                "Chrome",
                "macOS",
                "127.0.0.1");
        RefreshToken phoneSession = createRefreshToken(
                200L,
                "session-other",
                user.getEmail(),
                "db-refresh-other",
                "mobile",
                "iPhone",
                "Safari",
                "iOS",
                "198.51.100.20");
        MockHttpServletRequest request = buildRequest(WINDOWS_CHROME_USER_AGENT, "stale-cookie", "203.0.113.10");

        when(userService.findUserById(1L)).thenReturn(user);
        when(refreshRepository.findAllByEmailOrderByIdDesc(user.getEmail()))
                .thenReturn(List.of(phoneSession, macSession));
        when(jwtUtil.getSessionId("stale-cookie")).thenReturn("missing-session");

        assertThatThrownBy(() -> controller.deleteSessions(1L, true, request))
                .isInstanceOf(ConflictBusinessException.class)
                .hasMessageContaining("현재 세션을 확인하지 못해");
        verify(refreshRepository, never()).deleteAll(anyIterable());
    }

    private UserEntity createUser(Long id, String email) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setEmail(email);
        return user;
    }

    private RefreshToken createRefreshToken(
            Long id,
            String sessionId,
            String email,
            String token,
            String deviceType,
            String deviceLabel,
            String browser,
            String os,
            String ip) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setId(id);
        refreshToken.setSessionId(sessionId);
        refreshToken.setEmail(email);
        refreshToken.setToken(token);
        refreshToken.setDeviceType(deviceType);
        refreshToken.setDeviceLabel(deviceLabel);
        refreshToken.setBrowser(browser);
        refreshToken.setOs(os);
        refreshToken.setIp(ip);
        refreshToken.setLastSeenAt(LocalDateTime.now().minusMinutes(1));
        refreshToken.setExpiryDate(LocalDateTime.now().plusDays(7));
        return refreshToken;
    }

    private MockHttpServletRequest buildRequest(String userAgent, String refreshCookie, String remoteAddress) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("User-Agent", userAgent);
        request.setRemoteAddr(remoteAddress);
        request.setCookies(new Cookie("Refresh", refreshCookie));
        return request;
    }

    private boolean readBooleanProperty(Object target, String methodName) throws Exception {
        Method method = target.getClass().getMethod(methodName);
        return (boolean) method.invoke(target);
    }

    private String readStringProperty(Object target, String methodName) throws Exception {
        Method method = target.getClass().getMethod(methodName);
        Object value = method.invoke(target);
        return value == null ? null : value.toString();
    }
}
