package com.example.auth.config;

import com.example.auth.entity.UserEntity;
import com.example.auth.entity.UserProvider;
import com.example.auth.repository.UserProviderRepository;
import com.example.auth.repository.UserRepository;
import com.example.mate.repository.SellerPayoutProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DevDataInitializerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserProviderRepository userProviderRepository;

    @Mock
    private SellerPayoutProfileRepository sellerPayoutProfileRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private DevDataInitializer devDataInitializer;

    @BeforeEach
    void setUp() {
        devDataInitializer = new DevDataInitializer(
                userRepository,
                userProviderRepository,
                sellerPayoutProfileRepository,
                passwordEncoder
        );
        ReflectionTestUtils.setField(devDataInitializer, "testEmail", "");
        ReflectionTestUtils.setField(devDataInitializer, "testPassword", "");
        ReflectionTestUtils.setField(devDataInitializer, "testHostEmail", "");
        ReflectionTestUtils.setField(devDataInitializer, "testHostPassword", "");
        ReflectionTestUtils.setField(devDataInitializer, "testBuyerEmail", "");
        ReflectionTestUtils.setField(devDataInitializer, "testBuyerPassword", "");
    }

    @Test
    @DisplayName("TEST_ADMIN credentials가 주어지면 ROLE_ADMIN 계정을 생성한다")
    void run_createsAdminSeedAccount() throws Exception {
        ReflectionTestUtils.setField(devDataInitializer, "testAdminEmail", "rhksflwk@mail.com");
        ReflectionTestUtils.setField(devDataInitializer, "testAdminPassword", "Rhksflwk1234@");

        when(userRepository.existsByEmail("rhksflwk@mail.com")).thenReturn(false);
        when(userRepository.findByHandle("@devadmin")).thenReturn(Optional.empty());
        when(userRepository.findByHandle("devadmin")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("Rhksflwk1234@")).thenReturn("encoded-admin-password");
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> {
            UserEntity candidate = invocation.getArgument(0);
            candidate.setId(99L);
            return candidate;
        });

        devDataInitializer.run();

        ArgumentCaptor<UserEntity> userCaptor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(userCaptor.capture());
        UserEntity savedUser = userCaptor.getValue();
        assertThat(savedUser.getEmail()).isEqualTo("rhksflwk@mail.com");
        assertThat(savedUser.getRole()).isEqualTo("ROLE_ADMIN");
        assertThat(savedUser.getPassword()).isEqualTo("encoded-admin-password");
        assertThat(savedUser.getHandle()).isEqualTo("@devadmin");

        ArgumentCaptor<UserProvider> providerCaptor = ArgumentCaptor.forClass(UserProvider.class);
        verify(userProviderRepository).save(providerCaptor.capture());
        assertThat(providerCaptor.getValue().getUser().getId()).isEqualTo(99L);
        assertThat(providerCaptor.getValue().getEmail()).isEqualTo("rhksflwk@mail.com");
        verify(sellerPayoutProfileRepository, never()).save(any());
    }

    @Test
    @DisplayName("기존 admin seed 계정이 ROLE_USER여도 ROLE_ADMIN으로 승격 동기화한다")
    void run_upgradesExistingSeedAccountToAdminRole() throws Exception {
        ReflectionTestUtils.setField(devDataInitializer, "testAdminEmail", "rhksflwk@mail.com");
        ReflectionTestUtils.setField(devDataInitializer, "testAdminPassword", "Rhksflwk1234@");

        UserEntity existingUser = UserEntity.builder()
                .id(77L)
                .email("rhksflwk@mail.com")
                .handle("devadmin")
                .name("Dev Admin")
                .password("old-password")
                .role("ROLE_USER")
                .enabled(false)
                .locked(true)
                .cheerPoints(10)
                .providers(new ArrayList<>())
                .build();

        when(userRepository.existsByEmail("rhksflwk@mail.com")).thenReturn(true);
        when(userRepository.findWithProvidersByEmail("rhksflwk@mail.com")).thenReturn(Optional.of(existingUser));
        when(userRepository.findByHandle("@devadmin")).thenReturn(Optional.empty());
        when(userRepository.findByHandle("devadmin")).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches("Rhksflwk1234@", "old-password")).thenReturn(false);
        when(passwordEncoder.encode("Rhksflwk1234@")).thenReturn("encoded-admin-password");
        when(userProviderRepository.save(any(UserProvider.class))).thenAnswer(invocation -> invocation.getArgument(0));

        devDataInitializer.run();

        assertThat(existingUser.getRole()).isEqualTo("ROLE_ADMIN");
        assertThat(existingUser.isEnabled()).isTrue();
        assertThat(existingUser.isLocked()).isFalse();
        assertThat(existingUser.getCheerPoints()).isEqualTo(100000);
        assertThat(existingUser.getPassword()).isEqualTo("encoded-admin-password");
        assertThat(existingUser.getHandle()).isEqualTo("@devadmin");
        verify(userRepository).save(existingUser);
    }
}
