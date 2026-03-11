package com.example.auth.oauth2;

import com.example.auth.entity.UserEntity;
import com.example.auth.entity.UserProvider;
import com.example.auth.repository.UserProviderRepository;
import com.example.auth.repository.UserRepository;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
    @DisplayName("authoritative provider email should auto-link existing OAuth-only account")
    void processNormalLogin_allowsAuthoritativeAutoLink() throws Exception {
        UserEntity existingUser = oauthOnlyUser(10L, "social@example.com");
        when(userRepository.findByEmail("social@example.com")).thenReturn(Optional.of(existingUser));
        when(userProviderRepository.findByUserIdAndProvider(existingUser.getId(), "google")).thenReturn(Optional.empty());

        UserEntity result = invokeProcessNormalLogin(
                Optional.empty(),
                "social@example.com",
                "소셜유저",
                "google",
                "google-provider-id",
                null,
                true);

        assertThat(result).isSameAs(existingUser);
        verify(userProviderRepository).save(org.mockito.ArgumentMatchers.any(UserProvider.class));
    }

    @Test
    @DisplayName("non-authoritative provider email should require manual link for existing OAuth-only account")
    void processNormalLogin_requiresManualLinkWhenProviderEmailNotAuthoritative() {
        UserEntity existingUser = oauthOnlyUser(11L, "social@example.com");
        when(userRepository.findByEmail("social@example.com")).thenReturn(Optional.of(existingUser));

        assertThatThrownBy(() -> invokeProcessNormalLogin(
                Optional.empty(),
                "social@example.com",
                "소셜유저",
                "naver",
                "naver-provider-id",
                null,
                false))
                .isInstanceOfSatisfying(OAuth2AuthenticationException.class, exception ->
                        assertThat(exception.getError().getErrorCode()).isEqualTo("manual_link_required"));
    }

    @Test
    @DisplayName("password-protected account should remain blocked from social auto-link")
    void processNormalLogin_blocksPasswordProtectedAccount() {
        UserEntity existingUser = UserEntity.builder()
                .id(12L)
                .uniqueId(UUID.randomUUID())
                .handle("handle12")
                .name("기존유저")
                .email("local@example.com")
                .password("hashed-password")
                .role("ROLE_USER")
                .build();
        when(userRepository.findByEmail("local@example.com")).thenReturn(Optional.of(existingUser));

        assertThatThrownBy(() -> invokeProcessNormalLogin(
                Optional.empty(),
                "local@example.com",
                "로컬유저",
                "google",
                "google-provider-id",
                null,
                true))
                .isInstanceOfSatisfying(OAuth2AuthenticationException.class, exception ->
                        assertThat(exception.getError().getErrorCode()).contains("ACCOUNT_EXISTS_WITH_PASSWORD"));
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
            String profileImageUrl,
            boolean authoritativeForAutoLink) throws Exception {
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

        try {
            return (UserEntity) method.invoke(
                    customOAuth2UserService,
                    userProviderOpt,
                    email,
                    userName,
                    provider,
                    providerId,
                    profileImageUrl,
                    authoritativeForAutoLink);
        } catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw e;
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
