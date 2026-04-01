package com.example.auth.oauth2;

import com.example.auth.entity.UserEntity;
import com.example.auth.entity.UserProvider;
import com.example.auth.repository.UserProviderRepository;
import com.example.auth.repository.UserRepository;
import com.example.auth.service.AuthSecurityMonitoringService;
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
