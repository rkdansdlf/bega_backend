package com.example.auth.oauth2;

import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserProviderRepository;
import com.example.auth.repository.UserRepository;
import com.example.bega.auth.service.OAuth2LinkStateService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import jakarta.servlet.http.HttpServletRequest;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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

    private void invokeApplyProfileImage(UserEntity user, String profileImageUrl) throws Exception {
        Method method = CustomOAuth2UserService.class
                .getDeclaredMethod("applyProfileImageFromOAuth", UserEntity.class, String.class);
        method.setAccessible(true);
        method.invoke(customOAuth2UserService, user, profileImageUrl);
    }
}
