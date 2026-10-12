package com.example.auth.oauth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.auth.entity.UserEntity;
import com.example.auth.entity.UserProvider;
import com.example.auth.repository.UserProviderRepository;
import com.example.auth.repository.UserRepository;
import com.example.auth.service.AuthSecurityMonitoringService;
import com.example.auth.service.AuthSessionService;
import com.example.auth.service.OAuth2StateService;
import com.example.auth.service.UserService;
import com.example.auth.util.JWTUtil;
import com.example.bega.auth.dto.OAuth2LinkStateData;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

@ExtendWith(MockitoExtension.class)
class OAuth2CallbackFinalizationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserProviderRepository userProviderRepository;

    @Mock
    private UserService userService;

    @Mock
    private OAuth2StateService oAuth2StateService;

    @Mock
    private AuthSessionService authSessionService;

    @Mock
    private AuthSecurityMonitoringService securityMonitoringService;

    @Mock
    private JWTUtil jwtUtil;

    @Mock
    private HttpServletRequest request;

    private OAuth2CallbackFinalizationService service;

    @BeforeEach
    void setUp() {
        service = new OAuth2CallbackFinalizationService(
                userRepository,
                userProviderRepository,
                userService,
                oAuth2StateService,
                authSessionService,
                securityMonitoringService,
                jwtUtil);
        lenient().when(jwtUtil.getAccessTokenExpirationTime()).thenReturn(900_000L);
        lenient().when(jwtUtil.getRefreshTokenExpirationTime()).thenReturn(604_800_000L);
        lenient().when(jwtUtil.createJwt(anyString(), anyString(), anyLong(), anyLong(), any()))
                .thenReturn("access-token");
    }

    @Test
    @DisplayName("비활성 계정은 프로필·보너스·세션 side effect 전에 OAuth 최종화를 거부한다")
    void finalizeCallback_rejectsDisabledAccountBeforeSideEffects() {
        UserEntity user = linkedUser();
        user.setEnabled(false);
        stubExistingProviderLogin(user);

        assertRejectedAs(intent(), "oauth2_account_not_eligible");

        verify(userRepository, never()).save(any(UserEntity.class));
        verify(userService, never()).checkAndApplyDailyLoginBonus(any(UserEntity.class));
        verifyNoInteractions(oAuth2StateService, authSessionService);
    }

    @Test
    @DisplayName("삭제 유예 계정은 enabled 여부와 별개로 OAuth 최종화를 거부한다")
    void finalizeCallback_rejectsPendingDeletionBeforeSideEffects() {
        UserEntity user = linkedUser();
        user.setPendingDeletion(true);
        stubExistingProviderLogin(user);

        assertRejectedAs(intent(), "oauth2_account_not_eligible");

        verify(userRepository, never()).save(any(UserEntity.class));
        verify(userService, never()).checkAndApplyDailyLoginBonus(any(UserEntity.class));
        verifyNoInteractions(oAuth2StateService, authSessionService);
    }

    @Test
    @DisplayName("현재 시각까지 유효한 계정 잠금은 OAuth 최종화를 거부한다")
    void finalizeCallback_rejectsActiveLockBeforeSideEffects() {
        UserEntity user = linkedUser();
        user.setLocked(true);
        user.setLockExpiresAt(LocalDateTime.now().plusMinutes(5));
        stubExistingProviderLogin(user);

        assertRejectedAs(intent(), "oauth2_account_not_eligible");

        verify(userRepository, never()).save(any(UserEntity.class));
        verify(userService, never()).checkAndApplyDailyLoginBonus(any(UserEntity.class));
        verifyNoInteractions(oAuth2StateService, authSessionService);
    }

    @Test
    @DisplayName("비활성 계정의 provider 연동은 association 변경 전에 거부한다")
    void finalizeCallback_rejectsIneligibleLinkTargetBeforeProviderWrites() {
        UserEntity user = linkedUser();
        user.setEnabled(false);
        when(userRepository.findByIdForWrite(21L)).thenReturn(Optional.of(user));

        assertRejectedAs(linkIntent(), "oauth2_account_not_eligible");

        verifyNoInteractions(userProviderRepository, oAuth2StateService, authSessionService, userService);
    }

    @Test
    @DisplayName("이메일이 기존 계정에 속하면 자동 가입 대신 기존 manual link 계약을 유지한다")
    void finalizeCallback_requiresManualLinkForExistingEmailWithoutProviderLink() {
        when(userProviderRepository.findByProviderAndProviderId("google", "provider-id"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("oauth-user@example.com"))
                .thenReturn(Optional.of(linkedUser()));

        assertRejectedAs(intent(), "manual_link_required");

        verify(userRepository, never()).save(any(UserEntity.class));
        verifyNoInteractions(oAuth2StateService, authSessionService, userService);
    }

    @Test
    @DisplayName("같은 사용자·provider·providerId의 재연동은 멱등이며 이메일만 갱신한다")
    void finalizeCallback_isIdempotentForSameProviderIdentity() {
        UserEntity user = linkedUser();
        UserProvider currentProvider = UserProvider.builder()
                .id(3L)
                .user(user)
                .provider("google")
                .providerId("provider-id")
                .email("old@example.com")
                .build();
        when(userRepository.findByIdForWrite(21L)).thenReturn(Optional.of(user));
        when(userProviderRepository.findByUserIdAndProviderForUpdate(21L, "google"))
                .thenReturn(Optional.of(currentProvider));
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(oAuth2StateService.saveState(21L)).thenReturn("callback-state");
        when(authSessionService.issueRefreshToken(anyString(), anyString(), anyLong(), any(), eq(request)))
                .thenReturn("refresh-token");

        OAuth2CallbackFinalizationResult result = service.finalizeCallback(linkIntent(), request);

        assertThat(result.linkFlow()).isTrue();
        assertThat(currentProvider.getEmail()).isEqualTo("oauth-user@example.com");
        verify(userProviderRepository).save(currentProvider);
        verify(userService).checkAndApplyDailyLoginBonus(user);
    }

    @Test
    @DisplayName("만료된 잠금은 기존 정책대로 허용하고 state 저장 후 세션을 발급한다")
    void finalizeCallback_allowsExpiredLockAndPersistsStateBeforeSession() {
        UserEntity user = linkedUser();
        user.setLocked(true);
        user.setLockExpiresAt(LocalDateTime.now().minusMinutes(1));
        stubExistingProviderLogin(user);
        UserProvider provider = UserProvider.builder()
                .id(1L)
                .user(user)
                .provider("google")
                .providerId("provider-id")
                .email(user.getEmail())
                .build();
        when(userProviderRepository.findByProviderAndProviderIdForUpdate("google", "provider-id"))
                .thenReturn(Optional.of(provider));
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(oAuth2StateService.saveState(21L)).thenReturn("callback-state");
        when(authSessionService.issueRefreshToken(anyString(), anyString(), anyLong(), any(), eq(request)))
                .thenReturn("refresh-token");

        OAuth2CallbackFinalizationResult result = service.finalizeCallback(intent(), request);

        assertThat(result.user()).isSameAs(user);
        assertThat(result.stateId()).isEqualTo("callback-state");
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.accessTokenMaxAgeSeconds()).isEqualTo(900);
        assertThat(result.refreshTokenMaxAgeSeconds()).isEqualTo(604_800);
        verify(userService).checkAndApplyDailyLoginBonus(user);
        InOrder stateBeforeSession = inOrder(oAuth2StateService, authSessionService);
        stateBeforeSession.verify(oAuth2StateService).saveState(21L);
        stateBeforeSession.verify(authSessionService)
                .issueRefreshToken(anyString(), anyString(), anyLong(), any(), eq(request));
    }

    private void stubExistingProviderLogin(UserEntity user) {
        UserProvider provider = UserProvider.builder()
                .id(1L)
                .user(user)
                .provider("google")
                .providerId("provider-id")
                .email(user.getEmail())
                .build();
        when(userProviderRepository.findByProviderAndProviderId("google", "provider-id"))
                .thenReturn(Optional.of(provider));
        when(userRepository.findByIdForWrite(user.getId())).thenReturn(Optional.of(user));
    }

    private void assertRejectedAs(OAuth2LoginIntent loginIntent, String errorCode) {
        assertThatThrownBy(() -> service.finalizeCallback(loginIntent, request))
                .isInstanceOfSatisfying(OAuth2AuthenticationException.class, exception ->
                        assertThat(exception.getError().getErrorCode()).isEqualTo(errorCode));
    }

    private UserEntity linkedUser() {
        return UserEntity.builder()
                .id(21L)
                .uniqueId(UUID.randomUUID())
                .handle("oauth-user")
                .name("OAuth User")
                .email("oauth-user@example.com")
                .role("ROLE_USER")
                .profileImageUrl(null)
                .cheerPoints(0)
                .build();
    }

    private OAuth2LoginIntent intent() {
        return new OAuth2LoginIntent(
                "google",
                "provider-id",
                "oauth-user@example.com",
                "OAuth User",
                null,
                true,
                false,
                null);
    }

    private OAuth2LoginIntent linkIntent() {
        return new OAuth2LoginIntent(
                "google",
                "provider-id",
                "oauth-user@example.com",
                "OAuth User",
                null,
                true,
                false,
                new OAuth2LinkStateData(21L, System.currentTimeMillis(), null));
    }
}
