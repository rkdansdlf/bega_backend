package com.example.auth.service;

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
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Removes directly identifying account and authentication data after the recovery window closes. */
@Service
@RequiredArgsConstructor
public class AccountDeletionIdentityService {

    private static final LocalDateTime TOMBSTONE_CREATED_AT = LocalDateTime.of(1970, 1, 1, 0, 0);

    private final RefreshRepository refreshRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final OAuthEmailChallengeRepository oauthEmailChallengeRepository;
    private final TrustedDeviceRepository trustedDeviceRepository;
    private final AccountSecurityEventRepository accountSecurityEventRepository;
    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final CacheManager cacheManager;

    public static boolean isAnonymizedTombstone(UserEntity user) {
        if (user == null || user.getId() == null || user.isEnabled() || !user.isPendingDeletion()
                || user.getDeletionScheduledFor() != null || !user.isLocked()
                || !"탈퇴한 사용자".equals(user.getName())
                || user.getPassword() != null || user.getProvider() != null || user.getProviderId() != null) {
            return false;
        }
        String email = user.getEmail();
        String base = "deleted-" + user.getId();
        return email != null
                && email.endsWith("@deleted.invalid")
                && (email.equals(base + "@deleted.invalid") || email.startsWith(base + "-"));
    }

    @Transactional
    public void anonymize(UserEntity user) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("A persisted account is required for finalization.");
        }

        Long userId = user.getId();
        String originalEmail = user.getEmail();
        auditLogRepository.redactDeletedUserAuditValues(
                userId,
                List.of(
                        AuditLog.AuditAction.DELETE_USER,
                        AuditLog.AuditAction.DELETE_POST,
                        AuditLog.AuditAction.DELETE_MATE),
                "[REDACTED]");
        auditLogRepository.redactDeletedUserAuditDescriptions(
                userId,
                List.of(
                        AuditLog.AuditAction.PROMOTE_TO_ADMIN,
                        AuditLog.AuditAction.DEMOTE_TO_USER),
                "[REDACTED]");
        if (originalEmail != null && !originalEmail.isBlank()) {
            refreshRepository.deleteByEmail(originalEmail);
        }

        Set<ProviderIdentity> providerIdentities = new LinkedHashSet<>();
        for (UserProvider provider : user.getProviders()) {
            if (provider.getProvider() != null && provider.getProviderId() != null) {
                providerIdentities.add(new ProviderIdentity(provider.getProvider(), provider.getProviderId()));
            }
        }
        if (user.getProvider() != null && user.getProviderId() != null) {
            providerIdentities.add(new ProviderIdentity(user.getProvider(), user.getProviderId()));
        }
        for (ProviderIdentity identity : providerIdentities) {
            oauthEmailChallengeRepository.deleteByProviderAndProviderId(
                    identity.provider(), identity.providerId());
        }

        user.getProviders().clear();
        passwordResetTokenRepository.deleteByUserId(userId);
        trustedDeviceRepository.deleteByUserId(userId);
        accountSecurityEventRepository.anonymizeByUserId(userId);
        evictPredictionShareId(userId);

        user.setUniqueId(rotatedUniqueId());
        user.setHandle(tombstoneHandle(userId));
        user.setName("탈퇴한 사용자");
        user.setEmail(tombstoneEmail(userId));
        user.setPassword(null);
        user.setProvider(null);
        user.setProviderId(null);
        user.setProfileImageUrl(null);
        user.setProfileFeedImageUrl(null);
        user.setBio(null);
        user.setPrivateAccount(true);
        user.setFavoriteTeam(null);
        user.setCheerPoints(0);
        user.setLastBonusDate(null);
        user.setLastLoginDate(null);
        user.setRole("ROLE_USER");
        user.setEnabled(false);
        user.setPendingDeletion(true);
        user.setDeletionRequestedAt(null);
        user.setDeletionScheduledFor(null);
        user.setLocked(true);
        user.setLockExpiresAt(null);
        user.setCreatedAt(TOMBSTONE_CREATED_AT);
        if (userRepository.anonymizeCreatedAtById(userId, TOMBSTONE_CREATED_AT) != 1) {
            throw new IllegalStateException("The required account creation timestamp could not be anonymized.");
        }
    }

    private void evictPredictionShareId(Long userId) {
        Cache cache = cacheManager.getCache(CacheConfig.RANKING_SHARE_IDS);
        if (cache != null) {
            cache.evict(userId.toString());
        }
    }

    private String tombstoneHandle(Long userId) {
        String base = "d" + Long.toString(userId, 36);
        String candidate = base;
        long suffix = 0;
        while (Boolean.TRUE.equals(userRepository.existsByHandle(candidate))) {
            candidate = base + "-" + ++suffix;
        }
        return candidate;
    }

    private String tombstoneEmail(Long userId) {
        String base = "deleted-" + userId;
        String candidate = base + "@deleted.invalid";
        long suffix = 0;
        while (Boolean.TRUE.equals(userRepository.existsByEmail(candidate))) {
            candidate = base + "-" + ++suffix + "@deleted.invalid";
        }
        return candidate;
    }

    private UUID rotatedUniqueId() {
        UUID candidate;
        do {
            candidate = UUID.randomUUID();
        } while (userRepository.findByUniqueId(candidate).isPresent());
        return candidate;
    }

    private record ProviderIdentity(String provider, String providerId) {
    }
}
