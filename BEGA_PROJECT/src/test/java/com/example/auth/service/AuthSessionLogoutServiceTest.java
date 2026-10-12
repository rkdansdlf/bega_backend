package com.example.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.auth.entity.RefreshToken;
import com.example.auth.entity.UserEntity;
import com.example.auth.repository.RefreshRepository;
import com.example.auth.repository.UserRepository;
import com.example.auth.util.JWTUtil;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(MockitoExtension.class)
class AuthSessionLogoutServiceTest {

    @Mock private JWTUtil jwtUtil;
    @Mock private UserRepository userRepository;
    @Mock private RefreshRepository refreshRepository;
    @Mock private RefreshTokenDigestService digestService;
    @Mock private RefreshTokenReuseDetector reuseDetector;
    @Mock private RefreshTokenRevocationService revocationService;

    private AuthSessionLogoutService service;

    @BeforeEach
    void setUp() {
        service = new AuthSessionLogoutService(
                jwtUtil,
                userRepository,
                refreshRepository,
                digestService,
                reuseDetector,
                revocationService);
    }

    @Test
    void logout_deletesCurrentRefreshRowAfterLockingOwner() {
        UserEntity user = UserEntity.builder().id(9L).email("owner@test.com").build();
        RefreshToken token = new RefreshToken();
        token.setEmail(user.getEmail());
        when(jwtUtil.getTokenType("current-refresh")).thenReturn("refresh");
        when(jwtUtil.getUserId("current-refresh")).thenReturn(9L);
        when(digestService.digest("current-refresh")).thenReturn("digest-current");
        when(userRepository.findByIdForWrite(9L)).thenReturn(Optional.of(user));
        when(refreshRepository.findAllByTokenDigestForUpdate("digest-current")).thenReturn(List.of(token));

        service.revoke("current-refresh", 9L);

        InOrder order = inOrder(userRepository, refreshRepository);
        order.verify(userRepository).findByIdForWrite(9L);
        order.verify(refreshRepository).findAllByTokenDigestForUpdate("digest-current");
        verify(refreshRepository).deleteAll(List.of(token));
        verify(reuseDetector, never()).findReuseMarker("current-refresh");
    }

    @Test
    void logout_usesRotatedLegacyTokenMappingToRevokeOnlyItsSession() {
        UserEntity user = UserEntity.builder().id(10L).email("owner@test.com").build();
        when(jwtUtil.getTokenType("legacy-refresh")).thenReturn("refresh");
        when(jwtUtil.getUserId("legacy-refresh")).thenReturn(10L);
        when(digestService.digest("legacy-refresh")).thenReturn("digest-old");
        when(userRepository.findByIdForWrite(10L)).thenReturn(Optional.of(user));
        when(refreshRepository.findAllByTokenDigestForUpdate("digest-old")).thenReturn(List.of());
        when(reuseDetector.findReuseMarker("legacy-refresh")).thenReturn(Optional.of(
                new RefreshTokenReuseDetector.ReuseMarker(10L, "session-10")));

        service.revoke("legacy-refresh", 10L);

        verify(revocationService).revokeSessionAfterReuse(10L, "session-10");
        verify(revocationService, never()).revokeAllSessionsAfterReuse(10L);
    }

    @Test
    void logout_usesSignedSessionClaimWhenReuseMappingIsMissing() {
        UserEntity user = UserEntity.builder().id(12L).email("owner@test.com").build();
        when(jwtUtil.getTokenType("signed-refresh")).thenReturn("refresh");
        when(jwtUtil.getUserId("signed-refresh")).thenReturn(12L);
        when(jwtUtil.getSessionId("signed-refresh")).thenReturn("signed-session-12");
        when(digestService.digest("signed-refresh")).thenReturn("digest-signed");
        when(userRepository.findByIdForWrite(12L)).thenReturn(Optional.of(user));
        when(refreshRepository.findAllByTokenDigestForUpdate("digest-signed")).thenReturn(List.of());
        when(reuseDetector.findReuseMarker("signed-refresh")).thenReturn(Optional.empty());

        service.revoke("signed-refresh", 12L);

        verify(revocationService).revokeSessionAfterReuse(12L, "signed-session-12");
        verify(revocationService, never()).revokeAllSessionsAfterReuse(12L);
    }

    @Test
    void logoutFailsClosedWhenLegacyRefreshHasNoSafeSessionMapping() {
        UserEntity user = UserEntity.builder().id(13L).email("owner@test.com").build();
        when(jwtUtil.getTokenType("unmapped-legacy-refresh")).thenReturn("refresh");
        when(jwtUtil.getUserId("unmapped-legacy-refresh")).thenReturn(13L);
        when(digestService.digest("unmapped-legacy-refresh")).thenReturn("digest-unmapped");
        when(userRepository.findByIdForWrite(13L)).thenReturn(Optional.of(user));
        when(refreshRepository.findAllByTokenDigestForUpdate("digest-unmapped")).thenReturn(List.of());
        when(reuseDetector.findReuseMarker("unmapped-legacy-refresh")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.revoke("unmapped-legacy-refresh", 13L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("safe session mapping");

        verify(revocationService, never()).revokeSessionAfterReuse(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString());
        verify(revocationService, never()).revokeAllSessionsAfterReuse(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void logout_withoutRefreshCookiePreservesAccountWideFallbackUnderOwnerLock() {
        UserEntity user = UserEntity.builder().id(11L).email("owner@test.com").build();
        when(userRepository.findByIdForWrite(11L)).thenReturn(Optional.of(user));

        service.revoke(null, 11L);

        verify(userRepository).findByIdForWrite(11L);
        verify(refreshRepository).deleteByEmail("owner@test.com");
        verify(refreshRepository, never()).findAllByTokenDigestForUpdate(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void logout_withBlankRefreshCookieDoesNotBroadenToAccountWideRevocation() {
        service.revoke("   ", 11L);

        verify(userRepository, never()).findByIdForWrite(org.mockito.ArgumentMatchers.anyLong());
        verify(refreshRepository, never()).deleteByEmail(org.mockito.ArgumentMatchers.anyString());
        verify(refreshRepository, never()).findAllByTokenDigestForUpdate(org.mockito.ArgumentMatchers.anyString());
    }
}
