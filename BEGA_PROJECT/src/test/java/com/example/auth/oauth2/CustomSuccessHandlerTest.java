package com.example.auth.oauth2;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.auth.dto.CustomOAuth2User;
import com.example.auth.dto.UserDto;
import com.example.auth.entity.UserEntity;
import com.example.auth.repository.RefreshRepository;
import com.example.auth.repository.UserBlockRepository;
import com.example.auth.repository.UserFollowRepository;
import com.example.auth.repository.UserProviderRepository;
import com.example.auth.repository.UserRepository;
import com.example.auth.service.AccountDeletionService;
import com.example.auth.service.AccountSecurityService;
import com.example.auth.service.AuthSessionService;
import com.example.auth.service.OAuth2StateService;
import com.example.auth.service.UserService;
import com.example.auth.util.AuthCookieUtil;
import com.example.auth.util.JWTUtil;
import com.example.kbo.repository.TeamRepository;
import com.example.mate.service.PartyService;
import com.example.profile.storage.service.ProfileImageService;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CustomSuccessHandlerTest {

    @InjectMocks
    private UserService userService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private RefreshRepository refreshRepository;

    @Mock
    private UserFollowRepository userFollowRepository;

    @Mock
    private UserBlockRepository userBlockRepository;

    @Mock
    private UserProviderRepository userProviderRepository;

    @Mock
    private PasswordEncoder bCryptPasswordEncoder;

    @Mock
    private JWTUtil jwtUtil;

    @Mock
    private PartyService partyService;

    @Mock
    private ProfileImageService profileImageService;

    @Mock
    private AccountDeletionService accountDeletionService;

    @Mock
    private AccountSecurityService accountSecurityService;

    @Mock
    private AuthSessionService authSessionService;

    @Mock
    private OAuth2StateService oAuth2StateService;

    private CustomSuccessHandler customSuccessHandler;

    @BeforeEach
    void setUp() {
        customSuccessHandler = new CustomSuccessHandler(
                jwtUtil,
                userRepository,
                userService,
                accountSecurityService,
                oAuth2StateService,
                new AuthCookieUtil(false),
                authSessionService);
        ReflectionTestUtils.setField(customSuccessHandler, "frontendUrl", "http://localhost:5176");
    }

    @Test
    @DisplayName("정상 OAuth2 로그인은 같은 날 두 번 성공해도 출석 보너스를 한 번만 적립한다")
    void onAuthenticationSuccess_awardsDailyBonusOnlyOncePerDay() throws ServletException, IOException {
        UserEntity user = UserEntity.builder()
                .id(1L)
                .uniqueId(UUID.randomUUID())
                .email("oauth-user@example.com")
                .name("OAuth User")
                .handle("@oauthuser")
                .role("ROLE_USER")
                .provider("GOOGLE")
                .cheerPoints(0)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtUtil.createJwt(anyString(), anyString(), anyLong(), anyLong(), any()))
                .thenReturn("access-token-1", "access-token-2");
        when(authSessionService.issueRefreshToken(anyString(), anyString(), anyLong(), any(), any()))
                .thenReturn("refresh-token-1", "refresh-token-2");
        when(jwtUtil.getRefreshTokenExpirationTime()).thenReturn(604_800_000L);
        when(oAuth2StateService.saveState(1L)).thenReturn("state-one", "state-two");

        Authentication authentication = buildAuthentication();

        MockHttpServletResponse firstResponse = new MockHttpServletResponse();
        customSuccessHandler.onAuthenticationSuccess(buildRequest(), firstResponse, authentication);

        LocalDate firstBonusDate = user.getLastBonusDate();
        LocalDateTime firstLoginAt = user.getLastLoginDate();

        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        customSuccessHandler.onAuthenticationSuccess(buildRequest(), secondResponse, authentication);

        assertThat(user.getCheerPoints()).isEqualTo(5);
        assertThat(firstBonusDate)
                .isEqualTo(user.getLastBonusDate())
                .isEqualTo(LocalDate.now(ZoneId.of("Asia/Seoul")));
        assertThat(user.getLastLoginDate()).isNotNull().isAfterOrEqualTo(firstLoginAt);

        assertThat(firstResponse.getRedirectedUrl()).isEqualTo("http://localhost:5176/oauth/callback?state=state-one");
        assertThat(secondResponse.getRedirectedUrl()).isEqualTo("http://localhost:5176/oauth/callback?state=state-two");
        assertThat(firstResponse.getHeaders(HttpHeaders.SET_COOKIE))
                .anyMatch(header -> header.startsWith("Authorization="))
                .anyMatch(header -> header.startsWith("Refresh="))
                .anyMatch(header -> header.startsWith("XSRF-TOKEN=") && !header.contains("HttpOnly"));
        assertThat(secondResponse.getHeaders(HttpHeaders.SET_COOKIE))
                .anyMatch(header -> header.startsWith("Authorization="))
                .anyMatch(header -> header.startsWith("Refresh="))
                .anyMatch(header -> header.startsWith("XSRF-TOKEN=") && !header.contains("HttpOnly"));

        verify(userRepository, times(2)).save(user);
        verify(accountSecurityService, times(2)).handleSuccessfulLoginAsync(eq(user), any());
        verify(accountSecurityService, never()).handleSuccessfulLogin(eq(user), any());
    }

    @Test
    @DisplayName("명시적 linkFlow principal이면 linked 상태로 리다이렉트하고 provider linked 이벤트를 남긴다")
    void onAuthenticationSuccess_usesPrincipalLinkFlow() throws ServletException, IOException {
        UserEntity user = UserEntity.builder()
                .id(1L)
                .uniqueId(UUID.randomUUID())
                .email("oauth-user@example.com")
                .name("OAuth User")
                .handle("@oauthuser")
                .role("ROLE_USER")
                .provider("GOOGLE")
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(jwtUtil.createJwt(anyString(), anyString(), anyLong(), anyLong(), any()))
                .thenReturn("access-token");
        when(authSessionService.issueRefreshToken(anyString(), anyString(), anyLong(), any(), any()))
                .thenReturn("refresh-token");
        when(jwtUtil.getRefreshTokenExpirationTime()).thenReturn(604_800_000L);
        when(oAuth2StateService.saveState(1L)).thenReturn("linked-state");

        Authentication authentication = buildAuthentication(true);
        MockHttpServletRequest request = buildRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        customSuccessHandler.onAuthenticationSuccess(request, response, authentication);

        assertThat(response.getRedirectedUrl())
                .isEqualTo("http://localhost:5176/oauth/callback?state=linked-state&status=linked");
        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE))
                .anyMatch(header -> header.startsWith("XSRF-TOKEN=") && !header.contains("HttpOnly"));
        verify(accountSecurityService).recordProviderLinked(1L, "google");
        verify(accountSecurityService, never()).handleSuccessfulLoginAsync(eq(user), any());
        verify(accountSecurityService, never()).handleSuccessfulLogin(eq(user), any());
    }

    @Test
    @DisplayName("OAuth2 state가 CRLF를 포함해도 리다이렉트 Location 값은 인코딩된다")
    void onAuthenticationSuccess_encodesStateInRedirect() throws ServletException, IOException {
        UserEntity user = UserEntity.builder()
                .id(1L)
                .uniqueId(UUID.randomUUID())
                .email("oauth-user@example.com")
                .name("OAuth User")
                .handle("@oauthuser")
                .role("ROLE_USER")
                .provider("GOOGLE")
                .cheerPoints(0)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtUtil.createJwt(anyString(), anyString(), anyLong(), anyLong(), any()))
                .thenReturn("access-token");
        when(authSessionService.issueRefreshToken(anyString(), anyString(), anyLong(), any(), any()))
                .thenReturn("refresh-token");
        when(jwtUtil.getRefreshTokenExpirationTime()).thenReturn(604_800_000L);
        when(oAuth2StateService.saveState(1L)).thenReturn("state\r\nSet-Cookie: admin=true");

        MockHttpServletResponse response = new MockHttpServletResponse();

        customSuccessHandler.onAuthenticationSuccess(buildRequest(), response, buildAuthentication());

        assertThat(response.getRedirectedUrl())
                .isEqualTo("http://localhost:5176/oauth/callback?state=state%0D%0ASet-Cookie%3A+admin%3Dtrue");
        assertThat(response.getHeader(HttpHeaders.LOCATION)).doesNotContain("\r", "\n");
    }

    @Test
    @DisplayName("OAuth2 state 저장 실패 예외의 중첩 원문을 로그에 남기지 않음")
    void onAuthenticationSuccess_doesNotLogStateFromSaveFailure() throws ServletException, IOException {
        UserEntity user = UserEntity.builder()
                .id(1L)
                .uniqueId(UUID.randomUUID())
                .email("oauth-user@example.com")
                .name("OAuth User")
                .handle("@oauthuser")
                .role("ROLE_USER")
                .provider("GOOGLE")
                .build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(jwtUtil.getAccessTokenExpirationTime()).thenReturn(900_000L);
        when(jwtUtil.createJwt(anyString(), anyString(), anyLong(), anyLong(), any()))
                .thenReturn("synthetic-access-token");
        when(authSessionService.issueRefreshToken(anyString(), anyString(), anyLong(), any(), any()))
                .thenReturn("synthetic-refresh-token");
        when(jwtUtil.getRefreshTokenExpirationTime()).thenReturn(604_800_000L);
        String stateMarker = "state-id-in-cause-sentinel";
        when(oAuth2StateService.saveState(1L)).thenThrow(new OAuth2StateService.OAuth2StateStoreException(
                OAuth2StateService.ERROR_CODE_STATE_STORE_UNAVAILABLE,
                new IllegalStateException("Redis failed for oauth2:state:" + stateMarker)));

        Logger logger = (Logger) LoggerFactory.getLogger(CustomSuccessHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            customSuccessHandler.onAuthenticationSuccess(
                    buildRequest(), new MockHttpServletResponse(), buildAuthentication(true));

            assertThat(appender.list).allSatisfy(event -> {
                assertThat(event.getFormattedMessage()).doesNotContain(stateMarker);
                for (var throwable = event.getThrowableProxy(); throwable != null;
                        throwable = throwable.getCause()) {
                    assertThat(throwable.getMessage()).doesNotContain(stateMarker);
                }
            });
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    @DisplayName("OAuth2 linkFlow state가 CRLF를 포함해도 linked 리다이렉트 Location 값은 인코딩된다")
    void onAuthenticationSuccess_encodesLinkFlowStateInRedirect() throws ServletException, IOException {
        UserEntity user = UserEntity.builder()
                .id(1L)
                .uniqueId(UUID.randomUUID())
                .email("oauth-user@example.com")
                .name("OAuth User")
                .handle("@oauthuser")
                .role("ROLE_USER")
                .provider("GOOGLE")
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(jwtUtil.createJwt(anyString(), anyString(), anyLong(), anyLong(), any()))
                .thenReturn("access-token");
        when(authSessionService.issueRefreshToken(anyString(), anyString(), anyLong(), any(), any()))
                .thenReturn("refresh-token");
        when(jwtUtil.getRefreshTokenExpirationTime()).thenReturn(604_800_000L);
        when(oAuth2StateService.saveState(1L)).thenReturn("linked\r\nSet-Cookie: admin=true");

        MockHttpServletResponse response = new MockHttpServletResponse();

        customSuccessHandler.onAuthenticationSuccess(buildRequest(), response, buildAuthentication(true));

        assertThat(response.getRedirectedUrl())
                .isEqualTo("http://localhost:5176/oauth/callback?state=linked%0D%0ASet-Cookie%3A+admin%3Dtrue&status=linked");
        assertThat(response.getHeader(HttpHeaders.LOCATION)).doesNotContain("\r", "\n");
        verify(accountSecurityService).recordProviderLinked(1L, "google");
    }

    @Test
    @DisplayName("비활성화된 계정은 토큰과 쿠키를 발급하지 않고 oauth2_account_not_eligible 에러로 리다이렉트한다")
    void onAuthenticationSuccess_rejectsDisabledUser() throws ServletException, IOException {
        UserEntity user = UserEntity.builder()
                .id(1L)
                .uniqueId(UUID.randomUUID())
                .email("oauth-user@example.com")
                .name("OAuth User")
                .handle("@oauthuser")
                .role("ROLE_USER")
                .provider("GOOGLE")
                .enabled(false)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        MockHttpServletRequest request = buildRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        customSuccessHandler.onAuthenticationSuccess(request, response, buildAuthentication());

        assertThat(response.getRedirectedUrl())
                .isEqualTo("http://localhost:5176/login?error=oauth2_account_not_eligible");
        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
        verify(jwtUtil, never()).createJwt(anyString(), anyString(), anyLong(), anyLong(), any());
        verify(authSessionService, never()).issueRefreshToken(anyString(), anyString(), anyLong(), any(), any());
        verify(oAuth2StateService, never()).saveState(anyLong());
        verify(accountSecurityService, never()).handleSuccessfulLoginAsync(any(), any());
    }

    @Test
    @DisplayName("삭제 유예 상태인 계정은 토큰과 쿠키를 발급하지 않고 oauth2_account_not_eligible 에러로 리다이렉트한다")
    void onAuthenticationSuccess_rejectsPendingDeletionUser() throws ServletException, IOException {
        UserEntity user = UserEntity.builder()
                .id(1L)
                .uniqueId(UUID.randomUUID())
                .email("oauth-user@example.com")
                .name("OAuth User")
                .handle("@oauthuser")
                .role("ROLE_USER")
                .provider("GOOGLE")
                .pendingDeletion(true)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        MockHttpServletRequest request = buildRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        customSuccessHandler.onAuthenticationSuccess(request, response, buildAuthentication());

        assertThat(response.getRedirectedUrl())
                .isEqualTo("http://localhost:5176/login?error=oauth2_account_not_eligible");
        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
        verify(jwtUtil, never()).createJwt(anyString(), anyString(), anyLong(), anyLong(), any());
        verify(authSessionService, never()).issueRefreshToken(anyString(), anyString(), anyLong(), any(), any());
        verify(oAuth2StateService, never()).saveState(anyLong());
        verify(accountSecurityService, never()).handleSuccessfulLoginAsync(any(), any());
    }

    @Test
    @DisplayName("활성 잠금 상태인 계정은 토큰과 쿠키를 발급하지 않고 oauth2_account_not_eligible 에러로 리다이렉트한다")
    void onAuthenticationSuccess_rejectsActiveLockedUser() throws ServletException, IOException {
        UserEntity user = UserEntity.builder()
                .id(1L)
                .uniqueId(UUID.randomUUID())
                .email("oauth-user@example.com")
                .name("OAuth User")
                .handle("@oauthuser")
                .role("ROLE_USER")
                .provider("GOOGLE")
                .locked(true)
                .lockExpiresAt(LocalDateTime.now().plusHours(1))
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        MockHttpServletRequest request = buildRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        customSuccessHandler.onAuthenticationSuccess(request, response, buildAuthentication());

        assertThat(response.getRedirectedUrl())
                .isEqualTo("http://localhost:5176/login?error=oauth2_account_not_eligible");
        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
        verify(jwtUtil, never()).createJwt(anyString(), anyString(), anyLong(), anyLong(), any());
        verify(authSessionService, never()).issueRefreshToken(anyString(), anyString(), anyLong(), any(), any());
        verify(oAuth2StateService, never()).saveState(anyLong());
        verify(accountSecurityService, never()).handleSuccessfulLoginAsync(any(), any());
    }

    @Test
    @DisplayName("잠금이 만료된 계정은 정상 로그인으로 간주하여 토큰과 쿠키를 발급한다")
    void onAuthenticationSuccess_allowsExpiredLockedUser() throws ServletException, IOException {
        UserEntity user = UserEntity.builder()
                .id(1L)
                .uniqueId(UUID.randomUUID())
                .email("oauth-user@example.com")
                .name("OAuth User")
                .handle("@oauthuser")
                .role("ROLE_USER")
                .provider("GOOGLE")
                .locked(true)
                .lockExpiresAt(LocalDateTime.now().minusMinutes(10))
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(jwtUtil.createJwt(anyString(), anyString(), anyLong(), anyLong(), any())).thenReturn("access-token");
        when(authSessionService.issueRefreshToken(anyString(), anyString(), anyLong(), any(), any())).thenReturn("refresh-token");
        when(jwtUtil.getRefreshTokenExpirationTime()).thenReturn(604_800_000L);
        when(oAuth2StateService.saveState(1L)).thenReturn("state-valid");

        MockHttpServletRequest request = buildRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        customSuccessHandler.onAuthenticationSuccess(request, response, buildAuthentication());

        assertThat(response.getRedirectedUrl())
                .isEqualTo("http://localhost:5176/oauth/callback?state=state-valid");
        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE))
                .anyMatch(header -> header.startsWith("Authorization="))
                .anyMatch(header -> header.startsWith("Refresh="));
        verify(jwtUtil).createJwt(anyString(), anyString(), anyLong(), anyLong(), any());
        verify(authSessionService).issueRefreshToken(anyString(), anyString(), anyLong(), any(), any());
    }

    @Test
    @DisplayName("계정 연동 모드라도 부적격 계정이면 토큰 갱신 없이 에러로 리다이렉트한다")
    void onAuthenticationSuccess_linkFlow_rejectsIneligibleUser() throws ServletException, IOException {
        UserEntity user = UserEntity.builder()
                .id(1L)
                .uniqueId(UUID.randomUUID())
                .email("oauth-user@example.com")
                .name("OAuth User")
                .handle("@oauthuser")
                .role("ROLE_USER")
                .provider("GOOGLE")
                .enabled(false)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        MockHttpServletRequest request = buildRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        customSuccessHandler.onAuthenticationSuccess(request, response, buildAuthentication(true));

        assertThat(response.getRedirectedUrl())
                .isEqualTo("http://localhost:5176/login?error=oauth2_account_not_eligible");
        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
        verify(jwtUtil, never()).createJwt(anyString(), anyString(), anyLong(), anyLong(), any());
        verify(authSessionService, never()).issueRefreshToken(anyString(), anyString(), anyLong(), any(), any());
        verify(accountSecurityService, never()).recordProviderLinked(anyLong(), anyString());
    }

    private Authentication buildAuthentication() {
        return buildAuthentication(false);
    }

    private Authentication buildAuthentication(boolean linkFlow) {
        UserDto userDto = UserDto.builder()
                .id(1L)
                .email("oauth-user@example.com")
                .name("OAuth User")
                .role("ROLE_USER")
                .provider("GOOGLE")
                .build();
        CustomOAuth2User principal = new CustomOAuth2User(userDto, Map.of("sub", "oauth-user-sub"), linkFlow);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    private MockHttpServletRequest buildRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.setRequestURI("/login/oauth2/code/google");
        request.addHeader("User-Agent", "JUnit");
        return request;
    }
}
