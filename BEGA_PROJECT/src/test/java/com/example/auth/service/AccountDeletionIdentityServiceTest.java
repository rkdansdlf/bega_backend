package com.example.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.auth.entity.UserEntity;
import com.example.auth.entity.UserProvider;
import com.example.auth.repository.AccountSecurityEventRepository;
import com.example.auth.repository.OAuthEmailChallengeRepository;
import com.example.auth.repository.PasswordResetTokenRepository;
import com.example.auth.repository.RefreshRepository;
import com.example.auth.repository.TrustedDeviceRepository;
import com.example.auth.repository.UserRepository;
import com.example.admin.entity.AuditLog;
import com.example.admin.repository.AuditLogRepository;
import com.example.common.config.CacheConfig;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

@ExtendWith(MockitoExtension.class)
class AccountDeletionIdentityServiceTest {

    @Mock
    private RefreshRepository refreshRepository;
    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock
    private OAuthEmailChallengeRepository oauthEmailChallengeRepository;
    @Mock
    private TrustedDeviceRepository trustedDeviceRepository;
    @Mock
    private AccountSecurityEventRepository accountSecurityEventRepository;
    @Mock
    private AuditLogRepository auditLogRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CacheManager cacheManager;
    @Mock
    private Cache rankingShareIdCache;

    @Test
    void removesDirectIdentityAndAuthenticationDataAndRevokesPredictionShareId() {
        UUID oldUniqueId = UUID.randomUUID();
        UserEntity user = UserEntity.builder()
                .id(44L)
                .uniqueId(oldUniqueId)
                .handle("former-user")
                .name("Synthetic Person")
                .email("person@example.test")
                .password("synthetic-password-hash")
                .provider("GOOGLE")
                .providerId("synthetic-provider-id")
                .profileImageUrl("https://example.test/profile.png")
                .profileFeedImageUrl("https://example.test/feed.png")
                .bio("synthetic bio")
                .favoriteTeam(null)
                .cheerPoints(270)
                .lastBonusDate(java.time.LocalDate.now())
                .lastLoginDate(LocalDateTime.now())
                .createdAt(LocalDateTime.of(2020, 5, 4, 12, 30))
                .role("ROLE_ADMIN")
                .enabled(false)
                .pendingDeletion(true)
                .deletionRequestedAt(LocalDateTime.now().minusDays(7))
                .deletionScheduledFor(LocalDateTime.now().minusSeconds(1))
                .tokenVersion(2)
                .build();
        UserProvider provider = UserProvider.builder()
                .user(user)
                .provider("GOOGLE")
                .providerId("synthetic-provider-id")
                .email("person@example.test")
                .build();
        user.setProviders(new java.util.ArrayList<>(List.of(provider)));
        when(cacheManager.getCache(CacheConfig.RANKING_SHARE_IDS)).thenReturn(rankingShareIdCache);
        when(userRepository.anonymizeCreatedAtById(44L, LocalDateTime.of(1970, 1, 1, 0, 0))).thenReturn(1);

        AccountDeletionIdentityService service = new AccountDeletionIdentityService(
                refreshRepository,
                passwordResetTokenRepository,
                oauthEmailChallengeRepository,
                trustedDeviceRepository,
                accountSecurityEventRepository,
                auditLogRepository,
                userRepository,
                cacheManager);

        service.anonymize(user);

        assertThat(user.getId()).isEqualTo(44L);
        assertThat(user.getUniqueId()).isNotEqualTo(oldUniqueId);
        assertThat(user.getHandle()).isEqualTo("d18");
        assertThat(user.getName()).isEqualTo("탈퇴한 사용자");
        assertThat(user.getEmail()).isEqualTo("deleted-44@deleted.invalid");
        assertThat(user.getPassword()).isNull();
        assertThat(user.getProvider()).isNull();
        assertThat(user.getProviderId()).isNull();
        assertThat(user.getProviders()).isEmpty();
        assertThat(user.getProfileImageUrl()).isNull();
        assertThat(user.getProfileFeedImageUrl()).isNull();
        assertThat(user.getBio()).isNull();
        assertThat(user.getCheerPoints()).isZero();
        assertThat(user.getLastBonusDate()).isNull();
        assertThat(user.getLastLoginDate()).isNull();
        assertThat(user.getCreatedAt()).isEqualTo(LocalDateTime.of(1970, 1, 1, 0, 0));
        assertThat(user.getRole()).isEqualTo("ROLE_USER");
        assertThat(user.isEnabled()).isFalse();
        assertThat(user.isPendingDeletion()).isTrue();
        assertThat(user.getDeletionRequestedAt()).isNull();
        assertThat(user.getDeletionScheduledFor()).isNull();
        assertThat(user.getTokenVersion()).isEqualTo(2);

        InOrder cleanupOrder = inOrder(
                refreshRepository,
                oauthEmailChallengeRepository,
                passwordResetTokenRepository,
                trustedDeviceRepository,
                accountSecurityEventRepository,
                auditLogRepository,
                rankingShareIdCache);
        cleanupOrder.verify(auditLogRepository).redactDeletedUserAuditValues(
                44L,
                List.of(
                        AuditLog.AuditAction.DELETE_USER,
                        AuditLog.AuditAction.DELETE_POST,
                        AuditLog.AuditAction.DELETE_MATE),
                "[REDACTED]");
        cleanupOrder.verify(auditLogRepository).redactDeletedUserAuditDescriptions(
                44L,
                List.of(AuditLog.AuditAction.PROMOTE_TO_ADMIN, AuditLog.AuditAction.DEMOTE_TO_USER),
                "[REDACTED]");
        cleanupOrder.verify(refreshRepository).deleteByEmail("person@example.test");
        cleanupOrder.verify(oauthEmailChallengeRepository).deleteByProviderAndProviderId("GOOGLE", "synthetic-provider-id");
        cleanupOrder.verify(passwordResetTokenRepository).deleteByUserId(44L);
        cleanupOrder.verify(trustedDeviceRepository).deleteByUserId(44L);
        cleanupOrder.verify(accountSecurityEventRepository).anonymizeByUserId(44L);
        cleanupOrder.verify(rankingShareIdCache).evict("44");
        verify(userRepository).anonymizeCreatedAtById(44L, LocalDateTime.of(1970, 1, 1, 0, 0));
        verify(cacheManager).getCache(CacheConfig.RANKING_SHARE_IDS);
    }

    @Test
    void generatesCollisionFreeRequiredIdentityValues() {
        UserEntity user = UserEntity.builder()
                .id(44L).uniqueId(UUID.randomUUID()).handle("former-user")
                .name("Synthetic Person").email("person@example.test")
                .enabled(false).pendingDeletion(true)
                .deletionScheduledFor(LocalDateTime.now().minusSeconds(1))
                .build();
        user.setProviders(new java.util.ArrayList<>());
        when(userRepository.existsByHandle("d18")).thenReturn(true);
        when(userRepository.existsByHandle("d18-1")).thenReturn(false);
        when(userRepository.existsByEmail("deleted-44@deleted.invalid")).thenReturn(true);
        when(userRepository.existsByEmail("deleted-44-1@deleted.invalid")).thenReturn(false);
        when(cacheManager.getCache(CacheConfig.RANKING_SHARE_IDS)).thenReturn(rankingShareIdCache);
        when(userRepository.anonymizeCreatedAtById(44L, LocalDateTime.of(1970, 1, 1, 0, 0))).thenReturn(1);
        AccountDeletionIdentityService service = new AccountDeletionIdentityService(
                refreshRepository,
                passwordResetTokenRepository,
                oauthEmailChallengeRepository,
                trustedDeviceRepository,
                accountSecurityEventRepository,
                auditLogRepository,
                userRepository,
                cacheManager);

        service.anonymize(user);

        assertThat(user.getHandle()).isEqualTo("d18-1");
        assertThat(user.getEmail()).isEqualTo("deleted-44-1@deleted.invalid");
    }

    @Test
    void failsClosedWhenRequiredCreationTimestampCannotBeAnonymized() {
        UserEntity user = UserEntity.builder()
                .id(44L)
                .uniqueId(UUID.randomUUID())
                .handle("former-user")
                .name("Synthetic Person")
                .email("person@example.test")
                .enabled(false)
                .pendingDeletion(true)
                .build();
        user.setProviders(new java.util.ArrayList<>());
        AccountDeletionIdentityService service = new AccountDeletionIdentityService(
                refreshRepository,
                passwordResetTokenRepository,
                oauthEmailChallengeRepository,
                trustedDeviceRepository,
                accountSecurityEventRepository,
                auditLogRepository,
                userRepository,
                cacheManager);

        assertThatThrownBy(() -> service.anonymize(user))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("The required account creation timestamp could not be anonymized.");
    }
}
