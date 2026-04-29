package com.example.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.auth.entity.UserEntity;
import com.example.auth.repository.RefreshRepository;
import com.example.auth.repository.UserRepository;
import com.example.common.exception.RefreshTokenRevokeFailedException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefreshTokenRevocationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshRepository refreshRepository;

    private RefreshTokenRevocationService service;

    @BeforeEach
    void setUp() {
        service = new RefreshTokenRevocationService(userRepository, refreshRepository);
    }

    @Test
    void revokeAllSessionsAfterReuse_incrementsTokenVersionAndDeletesRefreshTokens() {
        UserEntity user = UserEntity.builder()
                .id(42L)
                .email("user@test.com")
                .tokenVersion(3)
                .build();
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));

        RefreshTokenRevocationService.RevokedRefreshSessions result =
                service.revokeAllSessionsAfterReuse(42L);

        assertThat(result.userId()).isEqualTo(42L);
        assertThat(result.email()).isEqualTo("user@test.com");
        assertThat(user.getTokenVersion()).isEqualTo(4);
        verify(userRepository).save(user);
        verify(refreshRepository).deleteByEmail("user@test.com");
    }

    @Test
    void revokeAllSessionsAfterReuse_rejectsMissingUser() {
        when(userRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.revokeAllSessionsAfterReuse(42L))
                .isInstanceOfSatisfying(RefreshTokenRevokeFailedException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(503);
                    assertThat(ex.getCode()).isEqualTo("REFRESH_TOKEN_REVOKE_FAILED");
                });

        verify(refreshRepository, never()).deleteByEmail(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void revokeAllSessionsAfterReuse_rejectsBlankEmail() {
        UserEntity user = UserEntity.builder()
                .id(42L)
                .email(" ")
                .tokenVersion(0)
                .build();
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.revokeAllSessionsAfterReuse(42L))
                .isInstanceOf(RefreshTokenRevokeFailedException.class);

        verify(userRepository, never()).save(user);
        verify(refreshRepository, never()).deleteByEmail(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void revokeAllSessionsAfterReuse_wrapsRepositoryFailureAs503() {
        when(userRepository.findById(42L)).thenThrow(new RuntimeException("db down"));

        assertThatThrownBy(() -> service.revokeAllSessionsAfterReuse(42L))
                .isInstanceOfSatisfying(RefreshTokenRevokeFailedException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(503);
                    assertThat(ex.getMessage()).isEqualTo(RefreshTokenRevokeFailedException.MESSAGE);
                    assertThat(ex.getMessage()).doesNotContain("db down");
                });
    }
}
