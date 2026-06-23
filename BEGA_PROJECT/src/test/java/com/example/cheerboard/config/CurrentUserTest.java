package com.example.cheerboard.config;

import com.example.auth.dto.CustomOAuth2User;
import com.example.auth.dto.UserDto;
import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserRepository;
import com.example.auth.service.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrentUserTest {

    @Mock
    private UserRepository userRepository;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getOrNull_reusesResolvedUserWithinRequest() {
        UserEntity user = usableUser(7L, 0);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        setAuthentication(7L, 0);
        CurrentUser currentUser = new CurrentUser(userRepository);

        assertThat(currentUser.getOrNull()).isSameAs(user);
        assertThat(currentUser.getOrNull()).isSameAs(user);

        verify(userRepository, times(1)).findById(7L);
        verify(userRepository, never()).existsUsableAuthorById(anyLong());
        verify(userRepository, never()).existsUsableAuthorByIdAndTokenVersion(anyLong(), anyInt());
    }

    @Test
    void getOrNull_returnsNullForInvalidTokenVersion() {
        UserEntity user = usableUser(7L, 2);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        setAuthentication(7L, 1);
        CurrentUser currentUser = new CurrentUser(userRepository);

        assertThat(currentUser.getOrNull()).isNull();

        verify(userRepository, times(1)).findById(7L);
        verify(userRepository, never()).existsUsableAuthorById(anyLong());
        verify(userRepository, never()).existsUsableAuthorByIdAndTokenVersion(anyLong(), anyInt());
    }

    @Test
    void getOrNull_usesCustomUserDetailsIdWithoutEmailLookup() {
        UserEntity user = usableUser(7L, 0, "details@example.test");
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        setAuthentication(new CustomUserDetails(user), 0);
        CurrentUser currentUser = new CurrentUser(userRepository);

        assertThat(currentUser.getOrNull()).isSameAs(user);

        verify(userRepository, times(1)).findById(7L);
        verify(userRepository, never()).findByEmail(anyString());
    }

    @Test
    void getOrNull_usesCustomOAuth2UserDtoIdWithoutEmailLookup() {
        UserEntity user = usableUser(7L, 0, "oauth@example.test");
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        CustomOAuth2User principal = new CustomOAuth2User(UserDto.builder()
                .id(7L)
                .email("oauth@example.test")
                .role("ROLE_USER")
                .build());
        setAuthentication(principal, 0);
        CurrentUser currentUser = new CurrentUser(userRepository);

        assertThat(currentUser.getOrNull()).isSameAs(user);

        verify(userRepository, times(1)).findById(7L);
        verify(userRepository, never()).findByEmail(anyString());
    }

    @Test
    void getOrNull_validatesEmailFallbackUserWithoutSecondIdLookup() {
        UserEntity user = usableUser(7L, 0, "fallback@example.test");
        when(userRepository.findByEmail("fallback@example.test")).thenReturn(Optional.of(user));
        setAuthentication("fallback@example.test", 0);
        CurrentUser currentUser = new CurrentUser(userRepository);

        assertThat(currentUser.getOrNull()).isSameAs(user);

        verify(userRepository, times(1)).findByEmail("fallback@example.test");
        verify(userRepository, never()).findById(anyLong());
    }

    @Test
    void getOrNull_returnsNullForInvalidTokenVersionOnEmailFallbackWithoutSecondIdLookup() {
        UserEntity user = usableUser(7L, 2, "fallback@example.test");
        when(userRepository.findByEmail("fallback@example.test")).thenReturn(Optional.of(user));
        setAuthentication("fallback@example.test", 1);
        CurrentUser currentUser = new CurrentUser(userRepository);

        assertThat(currentUser.getOrNull()).isNull();

        verify(userRepository, times(1)).findByEmail("fallback@example.test");
        verify(userRepository, never()).findById(anyLong());
    }

    @Test
    void getOrNull_returnsNullForAnonymousAuthentication() {
        AnonymousAuthenticationToken authentication = new AnonymousAuthenticationToken(
                "anonymous",
                "anonymousUser",
                List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        CurrentUser currentUser = new CurrentUser(userRepository);

        assertThat(currentUser.getOrNull()).isNull();

        verifyNoInteractions(userRepository);
    }

    private UserEntity usableUser(Long userId, Integer tokenVersion) {
        return UserEntity.builder()
                .id(userId)
                .enabled(true)
                .locked(false)
                .tokenVersion(tokenVersion)
                .role("ROLE_USER")
                .build();
    }

    private UserEntity usableUser(Long userId, Integer tokenVersion, String email) {
        return UserEntity.builder()
                .id(userId)
                .email(email)
                .enabled(true)
                .locked(false)
                .tokenVersion(tokenVersion)
                .role("ROLE_USER")
                .build();
    }

    private void setAuthentication(Object principal, Integer tokenVersion) {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        authentication.setDetails(tokenVersion);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
