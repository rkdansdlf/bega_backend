package com.example.auth.oauth2;

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
        ReflectionTestUtils.setField(customSuccessHandler, "frontendUrl", "http://localhost:5173");
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

        assertThat(firstResponse.getRedirectedUrl()).isEqualTo("http://localhost:5173/oauth/callback?state=state-one");
        assertThat(secondResponse.getRedirectedUrl()).isEqualTo("http://localhost:5173/oauth/callback?state=state-two");
        assertThat(firstResponse.getHeaders(HttpHeaders.SET_COOKIE))
                .anyMatch(header -> header.startsWith("Authorization="))
                .anyMatch(header -> header.startsWith("Refresh="));
        assertThat(secondResponse.getHeaders(HttpHeaders.SET_COOKIE))
                .anyMatch(header -> header.startsWith("Authorization="))
                .anyMatch(header -> header.startsWith("Refresh="));

        verify(userRepository, times(2)).save(user);
        verify(accountSecurityService, times(2)).handleSuccessfulLogin(eq(user), any());
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
                .isEqualTo("http://localhost:5173/oauth/callback?state=linked-state&status=linked");
        verify(accountSecurityService).recordProviderLinked(1L, "google");
        verify(accountSecurityService, never()).handleSuccessfulLogin(eq(user), any());
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
