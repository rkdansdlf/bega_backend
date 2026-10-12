package com.example.auth.oauth2;

import com.example.auth.entity.UserEntity;
import com.example.auth.entity.UserProvider;
import com.example.auth.repository.UserProviderRepository;
import com.example.auth.repository.UserRepository;
import com.example.auth.service.AuthSecurityMonitoringService;
import com.example.auth.service.OAuthEmailChallengeService;
import com.example.bega.auth.dto.OAuth2LinkStateData;
import com.example.bega.auth.service.OAuth2LinkStateService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomOAuth2UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserProviderRepository userProviderRepository;

    @Mock
    private HttpServletRequest request;

    @Mock
    private OAuth2LinkStateService oAuth2LinkStateService;

    @Mock
    private CookieAuthorizationRequestRepository cookieAuthorizationRequestRepository;

    @Mock
    private AuthSecurityMonitoringService securityMonitoringService;

    @Mock
    private OAuthEmailChallengeService oauthEmailChallengeService;

    @InjectMocks
    private CustomOAuth2UserService customOAuth2UserService;

    @Test
    void applyProfileImageFromOAuth_updatesWhenMissing() throws Exception {
        UserEntity user = UserEntity.builder()
                .id(1L)
                .email("user@example.com")
                .profileImageUrl(null)
                .build();

        invokeApplyProfileImage(user, "https://cdn.example.com/avatar.png");

        assertThat(user.getProfileImageUrl()).isEqualTo("https://cdn.example.com/avatar.png");
        verify(userRepository).save(user);
    }

    @Test
    void applyProfileImageFromOAuth_keepsExistingImage() throws Exception {
        UserEntity user = UserEntity.builder()
                .id(2L)
                .email("user2@example.com")
                .profileImageUrl("https://cdn.example.com/existing.png")
                .build();

        invokeApplyProfileImage(user, "https://cdn.example.com/new.png");

        assertThat(user.getProfileImageUrl()).isEqualTo("https://cdn.example.com/existing.png");
        verify(userRepository, never()).save(user);
    }

    @Test
    void applyProfileImageFromOAuth_ignoresNullImage() throws Exception {
        UserEntity user = UserEntity.builder()
                .id(3L)
                .email("user3@example.com")
                .profileImageUrl(null)
                .build();

        invokeApplyProfileImage(user, null);

        assertThat(user.getProfileImageUrl()).isNull();
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("동일 이메일 기존 계정은 provider와 무관하게 manual link를 요구한다")
    void processNormalLogin_requiresManualLinkForAnyExistingAccount() {
        UserEntity existingUser = oauthOnlyUser(10L, "social@example.com");
        when(userRepository.findByEmail("social@example.com")).thenReturn(Optional.of(existingUser));

        assertThatThrownBy(() -> invokeProcessNormalLogin(
                Optional.empty(),
                "social@example.com",
                "소셜유저",
                "google",
                "google-provider-id",
                null))
                .isInstanceOfSatisfying(OAuth2AuthenticationException.class, exception ->
                        assertThat(exception.getError().getErrorCode()).isEqualTo("manual_link_required"));
    }

    @Test
    @DisplayName("검증되지 않은 이메일의 신규 OAuth 가입은 앱 이메일 challenge를 요구한다")
    void processNormalLogin_unverifiedNewAccountRequiresChallenge() {
        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> invokeProcessNormalLogin(
                Optional.empty(),
                "new@example.com",
                "신규유저",
                "naver",
                "naver-provider-id",
                null,
                false))
                .isInstanceOfSatisfying(OAuth2AuthenticationException.class, exception ->
                        assertThat(exception.getError().getErrorCode())
                                .isEqualTo("oauth2_email_verification_required"));

        verify(oauthEmailChallengeService).issue(
                "naver", "naver-provider-id", "new@example.com", "신규유저", null);
        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any(UserEntity.class));
    }

    @Test
    @DisplayName("같은 provider 슬롯에 다른 providerId를 연결하려면 unlink가 선행되어야 한다")
    void processAccountLink_requiresUnlinkWhenProviderSlotAlreadyUsed() {
        UserEntity targetUser = oauthOnlyUser(11L, "social@example.com");
        UserProvider currentProvider = UserProvider.builder()
                .id(1L)
                .user(targetUser)
                .provider("google")
                .providerId("old-provider-id")
                .email("social@example.com")
                .build();

        when(userRepository.findByIdForWrite(11L)).thenReturn(Optional.of(targetUser));
        when(userProviderRepository.findByUserIdAndProviderForUpdate(11L, "google"))
                .thenReturn(Optional.of(currentProvider));

        assertThatThrownBy(() -> invokeProcessAccountLink(
                new OAuth2LinkStateData(11L, System.currentTimeMillis(), null),
                "google",
                "new-provider-id",
                "social@example.com"))
                .isInstanceOfSatisfying(OAuth2AuthenticationException.class, exception ->
                        assertThat(exception.getError().getErrorCode()).isEqualTo("oauth2_link_requires_unlink"));

        verify(securityMonitoringService).recordOAuth2LinkConflict();
    }

    @Test
    @DisplayName("이미 다른 사용자에 연동된 social account는 이전하지 않고 conflict로 차단한다")
    void processAccountLink_rejectsConflictingProviderOwner() {
        UserEntity targetUser = oauthOnlyUser(12L, "target@example.com");
        UserEntity ownerUser = oauthOnlyUser(13L, "owner@example.com");
        UserProvider existingProvider = UserProvider.builder()
                .id(2L)
                .user(ownerUser)
                .provider("google")
                .providerId("provider-id")
                .email("owner@example.com")
                .build();

        when(userRepository.findByIdForWrite(12L)).thenReturn(Optional.of(targetUser));
        when(userProviderRepository.findByUserIdAndProviderForUpdate(12L, "google"))
                .thenReturn(Optional.empty());
        when(userProviderRepository.findByProviderAndProviderIdForUpdate("google", "provider-id"))
                .thenReturn(Optional.of(existingProvider));

        assertThatThrownBy(() -> invokeProcessAccountLink(
                new OAuth2LinkStateData(12L, System.currentTimeMillis(), null),
                "google",
                "provider-id",
                "target@example.com"))
                .isInstanceOfSatisfying(OAuth2AuthenticationException.class, exception ->
                        assertThat(exception.getError().getErrorCode()).isEqualTo("oauth2_link_conflict"));
    }

    @Test
    @DisplayName("같은 user, 같은 provider, 같은 providerId면 idempotent success")
    void processAccountLink_isIdempotentForSameProviderId() {
        UserEntity targetUser = oauthOnlyUser(14L, "target@example.com");
        UserProvider currentProvider = UserProvider.builder()
                .id(3L)
                .user(targetUser)
                .provider("google")
                .providerId("provider-id")
                .email("old@example.com")
                .build();

        when(userRepository.findByIdForWrite(14L)).thenReturn(Optional.of(targetUser));
        when(userProviderRepository.findByUserIdAndProviderForUpdate(14L, "google"))
                .thenReturn(Optional.of(currentProvider));

        UserEntity result = invokeProcessAccountLink(
                new OAuth2LinkStateData(14L, System.currentTimeMillis(), null),
                "google",
                "provider-id",
                "new@example.com");

        assertThat(result).isSameAs(targetUser);
        assertThat(currentProvider.getEmail()).isEqualTo("new@example.com");
        verify(userProviderRepository).save(currentProvider);
    }

    private void invokeApplyProfileImage(UserEntity user, String profileImageUrl) throws Exception {
        Method method = CustomOAuth2UserService.class
                .getDeclaredMethod("applyProfileImageFromOAuth", UserEntity.class, String.class, String.class);
        method.setAccessible(true);
        method.invoke(customOAuth2UserService, user, profileImageUrl, "kakao");
    }

    private UserEntity invokeProcessNormalLogin(
            Optional<UserProvider> userProviderOpt,
            String email,
            String userName,
            String provider,
            String providerId,
            String profileImageUrl) {
        try {
            Method method = CustomOAuth2UserService.class.getDeclaredMethod(
                    "processNormalLogin",
                    Optional.class,
                    String.class,
                    String.class,
                    String.class,
                    String.class,
                    String.class);
            method.setAccessible(true);
            return (UserEntity) method.invoke(
                    customOAuth2UserService,
                    userProviderOpt,
                    email,
                    userName,
                    provider,
                    providerId,
                    profileImageUrl);
        } catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException(e);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private UserEntity invokeProcessNormalLogin(
            Optional<UserProvider> userProviderOpt,
            String email,
            String userName,
            String provider,
            String providerId,
            String profileImageUrl,
            boolean emailVerified) {
        try {
            Method method = CustomOAuth2UserService.class.getDeclaredMethod(
                    "processNormalLogin",
                    Optional.class,
                    String.class,
                    String.class,
                    String.class,
                    String.class,
                    String.class,
                    boolean.class);
            method.setAccessible(true);
            return (UserEntity) method.invoke(
                    customOAuth2UserService,
                    userProviderOpt,
                    email,
                    userName,
                    provider,
                    providerId,
                    profileImageUrl,
                    emailVerified);
        } catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException(e);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private UserEntity invokeProcessAccountLink(
            OAuth2LinkStateData linkData,
            String provider,
            String providerId,
            String email) {
        try {
            Method method = CustomOAuth2UserService.class.getDeclaredMethod(
                    "processAccountLink",
                    OAuth2LinkStateData.class,
                    String.class,
                    String.class,
                    String.class);
            method.setAccessible(true);
            return (UserEntity) method.invoke(customOAuth2UserService, linkData, provider, providerId, email);
        } catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException(e);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("일반 로그인: 비활성화된 계정은 oauth2_account_not_eligible 예외를 발생시킨다")
    void processNormalLogin_rejectsDisabledUser() {
        UserEntity disabledUser = oauthOnlyUser(20L, "disabled@example.com");
        disabledUser.setEnabled(false);

        UserProvider userProvider = UserProvider.builder()
                .id(10L)
                .user(disabledUser)
                .provider("google")
                .providerId("google-p-id")
                .email("disabled@example.com")
                .build();

        assertThatThrownBy(() -> invokeProcessNormalLogin(
                Optional.of(userProvider),
                "disabled@example.com",
                "비활성유저",
                "google",
                "google-p-id",
                null))
                .isInstanceOfSatisfying(OAuth2AuthenticationException.class, exception ->
                        assertThat(exception.getError().getErrorCode()).isEqualTo("oauth2_account_not_eligible"));
    }

    @Test
    @DisplayName("일반 로그인: 삭제 유예된 계정은 oauth2_account_not_eligible 예외를 발생시킨다")
    void processNormalLogin_rejectsPendingDeletionUser() {
        UserEntity pendingUser = oauthOnlyUser(21L, "pending@example.com");
        pendingUser.setPendingDeletion(true);

        UserProvider userProvider = UserProvider.builder()
                .id(11L)
                .user(pendingUser)
                .provider("google")
                .providerId("google-p-id-2")
                .email("pending@example.com")
                .build();

        assertThatThrownBy(() -> invokeProcessNormalLogin(
                Optional.of(userProvider),
                "pending@example.com",
                "삭제유예유저",
                "google",
                "google-p-id-2",
                null))
                .isInstanceOfSatisfying(OAuth2AuthenticationException.class, exception ->
                        assertThat(exception.getError().getErrorCode()).isEqualTo("oauth2_account_not_eligible"));
    }

    @Test
    @DisplayName("일반 로그인: 잠금된 계정(만료 전)은 oauth2_account_not_eligible 예외를 발생시킨다")
    void processNormalLogin_rejectsActiveLockedUser() {
        UserEntity lockedUser = oauthOnlyUser(22L, "locked@example.com");
        lockedUser.setLocked(true);
        lockedUser.setLockExpiresAt(java.time.LocalDateTime.now().plusHours(1));

        UserProvider userProvider = UserProvider.builder()
                .id(12L)
                .user(lockedUser)
                .provider("google")
                .providerId("google-p-id-3")
                .email("locked@example.com")
                .build();

        assertThatThrownBy(() -> invokeProcessNormalLogin(
                Optional.of(userProvider),
                "locked@example.com",
                "잠금유저",
                "google",
                "google-p-id-3",
                null))
                .isInstanceOfSatisfying(OAuth2AuthenticationException.class, exception ->
                        assertThat(exception.getError().getErrorCode()).isEqualTo("oauth2_account_not_eligible"));
    }

    @Test
    @DisplayName("일반 로그인: 잠금이 만료된 계정은 로그인을 허용한다")
    void processNormalLogin_allowsExpiredLockedUser() {
        UserEntity expiredLockedUser = oauthOnlyUser(23L, "expired-lock@example.com");
        expiredLockedUser.setLocked(true);
        expiredLockedUser.setLockExpiresAt(java.time.LocalDateTime.now().minusMinutes(5));

        UserProvider userProvider = UserProvider.builder()
                .id(13L)
                .user(expiredLockedUser)
                .provider("google")
                .providerId("google-p-id-4")
                .email("expired-lock@example.com")
                .build();

        UserEntity result = invokeProcessNormalLogin(
                Optional.of(userProvider),
                "expired-lock@example.com",
                "만료잠금유저",
                "google",
                "google-p-id-4",
                null);

        assertThat(result).isSameAs(expiredLockedUser);
    }

    @Test
    @DisplayName("계정 연동: 연동 대상 사용자가 비활성화 상태이면 oauth2_account_not_eligible 예외를 던진다")
    void processAccountLink_rejectsDisabledTargetUser() {
        UserEntity disabledUser = oauthOnlyUser(24L, "disabled-target@example.com");
        disabledUser.setEnabled(false);

        when(userRepository.findByIdForWrite(24L)).thenReturn(Optional.of(disabledUser));

        assertThatThrownBy(() -> invokeProcessAccountLink(
                new OAuth2LinkStateData(24L, System.currentTimeMillis(), null),
                "google",
                "new-provider-id",
                "disabled-target@example.com"))
                .isInstanceOfSatisfying(OAuth2AuthenticationException.class, exception ->
                        assertThat(exception.getError().getErrorCode()).isEqualTo("oauth2_account_not_eligible"));

        verify(userProviderRepository, never()).save(any(UserProvider.class));
        verify(userProviderRepository, never()).saveAndFlush(any(UserProvider.class));
    }

    @Test
    @DisplayName("계정 연동: 연동 대상 사용자가 삭제 유예 상태이면 oauth2_account_not_eligible 예외를 던진다")
    void processAccountLink_rejectsPendingDeletionTargetUser() {
        UserEntity pendingUser = oauthOnlyUser(25L, "pending-target@example.com");
        pendingUser.setPendingDeletion(true);

        when(userRepository.findByIdForWrite(25L)).thenReturn(Optional.of(pendingUser));

        assertThatThrownBy(() -> invokeProcessAccountLink(
                new OAuth2LinkStateData(25L, System.currentTimeMillis(), null),
                "google",
                "new-provider-id",
                "pending-target@example.com"))
                .isInstanceOfSatisfying(OAuth2AuthenticationException.class, exception ->
                        assertThat(exception.getError().getErrorCode()).isEqualTo("oauth2_account_not_eligible"));

        verify(userProviderRepository, never()).save(any(UserProvider.class));
        verify(userProviderRepository, never()).saveAndFlush(any(UserProvider.class));
    }

    @Test
    @DisplayName("계정 연동: 연동 대상 사용자가 잠금 상태(만료 전)이면 oauth2_account_not_eligible 예외를 던진다")
    void processAccountLink_rejectsActiveLockedTargetUser() {
        UserEntity lockedUser = oauthOnlyUser(26L, "locked-target@example.com");
        lockedUser.setLocked(true);
        lockedUser.setLockExpiresAt(java.time.LocalDateTime.now().plusMinutes(30));

        when(userRepository.findByIdForWrite(26L)).thenReturn(Optional.of(lockedUser));

        assertThatThrownBy(() -> invokeProcessAccountLink(
                new OAuth2LinkStateData(26L, System.currentTimeMillis(), null),
                "google",
                "new-provider-id",
                "locked-target@example.com"))
                .isInstanceOfSatisfying(OAuth2AuthenticationException.class, exception ->
                        assertThat(exception.getError().getErrorCode()).isEqualTo("oauth2_account_not_eligible"));

        verify(userProviderRepository, never()).save(any(UserProvider.class));
        verify(userProviderRepository, never()).saveAndFlush(any(UserProvider.class));
    }

    private UserEntity oauthOnlyUser(Long id, String email) {
        return UserEntity.builder()
                .id(id)
                .uniqueId(UUID.randomUUID())
                .handle("u" + id)
                .name("기존소셜유저")
                .email(email)
                .password(null)
                .role("ROLE_USER")
                .provider("GOOGLE")
                .build();
    }
}
