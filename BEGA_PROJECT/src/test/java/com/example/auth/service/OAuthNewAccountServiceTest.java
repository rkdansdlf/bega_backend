package com.example.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.auth.entity.UserEntity;
import com.example.auth.entity.UserProvider;
import com.example.auth.repository.UserProviderRepository;
import com.example.auth.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OAuthNewAccountServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserProviderRepository userProviderRepository = mock(UserProviderRepository.class);
    private final OAuthNewAccountService service = new OAuthNewAccountService(userRepository, userProviderRepository);

    @Test
    void socialAccountsCreatedFromVerifiedEmailAreMarkedVerified() {
        when(userProviderRepository.findByProviderAndProviderIdForUpdate("kakao", "pid")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("social@example.com")).thenReturn(Optional.empty());
        when(userRepository.saveAndFlush(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userProviderRepository.saveAndFlush(any(UserProvider.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserEntity created = service.create("social@example.com", "Social", "kakao", "pid", null);

        assertThat(created.isEmailVerified()).isTrue();
        assertThat(created.getEmailVerifiedAt()).isNotNull();
        assertThat(created.getPassword()).isNull();
    }
}
