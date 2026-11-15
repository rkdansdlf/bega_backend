package com.example.auth.oauth2;

import com.example.auth.entity.UserEntity;
import com.example.auth.entity.UserProvider;
import com.example.auth.repository.UserProviderRepository;
import com.example.auth.repository.UserRepository;
import com.example.auth.service.AuthSecurityMonitoringService;
import com.example.auth.service.AuthSessionService;
import com.example.auth.service.OAuth2StateService;
import com.example.auth.service.UserService;
import com.example.auth.util.AccountStatusUtil;
import com.example.auth.util.JWTUtil;
import com.example.bega.auth.dto.OAuth2LinkStateData;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class OAuth2CallbackFinalizationService {

    private static final String OAUTH2_ACCOUNT_NOT_ELIGIBLE = "oauth2_account_not_eligible";
    private static final String OAUTH2_PROVIDER_IDENTITY_CHANGED = "oauth2_provider_identity_changed";
    private static final String OAUTH2_LINK_CONFLICT = "oauth2_link_conflict";
    private static final String OAUTH2_LINK_REQUIRES_UNLINK = "oauth2_link_requires_unlink";
    private static final String OAUTH2_LINK_FAILED = "oauth2_link_failed";
    private static final String MANUAL_LINK_REQUIRED = "manual_link_required";

    private final UserRepository userRepository;
    private final UserProviderRepository userProviderRepository;
    private final UserService userService;
    private final OAuth2StateService oAuth2StateService;
    private final AuthSessionService authSessionService;
    private final AuthSecurityMonitoringService securityMonitoringService;
    private final JWTUtil jwtUtil;

    public OAuth2CallbackFinalizationService(
            UserRepository userRepository,
            UserProviderRepository userProviderRepository,
            @Lazy UserService userService,
            OAuth2StateService oAuth2StateService,
            AuthSessionService authSessionService,
            AuthSecurityMonitoringService securityMonitoringService,
            JWTUtil jwtUtil) {
        this.userRepository = userRepository;
        this.userProviderRepository = userProviderRepository;
        this.userService = userService;
        this.oAuth2StateService = oAuth2StateService;
        this.authSessionService = authSessionService;
        this.securityMonitoringService = securityMonitoringService;
        this.jwtUtil = jwtUtil;
    }

    @Transactional
    public OAuth2CallbackFinalizationResult finalizeCallback(
            OAuth2LoginIntent intent,
            HttpServletRequest request) {
        Objects.requireNonNull(intent, "OAuth2 login intent is required");
        Objects.requireNonNull(request, "OAuth2 callback request is required");

        UserEntity user = intent.isLinkFlow()
                ? processAccountLink(intent.linkData(), intent.provider(), intent.providerId(), intent.email())
                : processNormalLogin(intent);

        assertEligible(user);
        applyProfileImageFromOAuth(user, intent.profileImageUrl(), intent.provider());
        applyOAuthDailyBonus(user);
        userRepository.save(user);
        userService.checkAndApplyDailyLoginBonus(user);

        int tokenVersion = user.getTokenVersion() == null ? 0 : user.getTokenVersion();
        long accessTokenExpiresMs = jwtUtil.getAccessTokenExpirationTime();
        String accessToken = jwtUtil.createJwt(
                user.getEmail(), user.getRole(), user.getId(), accessTokenExpiresMs, tokenVersion);
        int accessTokenMaxAgeSeconds = (int) (accessTokenExpiresMs / 1000);
        int refreshTokenMaxAgeSeconds = (int) (jwtUtil.getRefreshTokenExpirationTime() / 1000);

        // Callback state must be durable before the refresh session is inserted.
        // Both writes join this transaction; a failure rolls back the account changes and session.
        String stateId = oAuth2StateService.saveState(user.getId());
        assertEligible(user);
        String refreshToken = authSessionService.issueRefreshToken(
                user.getEmail(), user.getRole(), user.getId(), tokenVersion, request);

        return new OAuth2CallbackFinalizationResult(
                user,
                user.toDto(),
                accessToken,
                accessTokenMaxAgeSeconds,
                refreshTokenMaxAgeSeconds,
                stateId,
                refreshToken,
                intent.isLinkFlow());
    }

    private UserEntity processNormalLogin(OAuth2LoginIntent intent) {
        Optional<UserProvider> linkedProvider = userProviderRepository
                .findByProviderAndProviderId(intent.provider(), intent.providerId());
        if (linkedProvider.isPresent()) {
            Long linkedUserId = linkedProvider.get().getUser().getId();
            UserEntity user = userRepository.findByIdForWrite(linkedUserId)
                    .orElseThrow(() -> new OAuth2AuthenticationException(OAUTH2_ACCOUNT_NOT_ELIGIBLE));
            assertEligible(user);

            Optional<UserProvider> lockedProvider = userProviderRepository
                    .findByProviderAndProviderIdForUpdate(intent.provider(), intent.providerId());
            if (lockedProvider.isEmpty()
                    || !Objects.equals(lockedProvider.get().getUser().getId(), user.getId())) {
                throw new OAuth2AuthenticationException(OAUTH2_PROVIDER_IDENTITY_CHANGED);
            }

            updateUser(user, intent.userName());
            return user;
        }

        if (intent.emailFromLinkedIdentity()) {
            throw new OAuth2AuthenticationException(OAUTH2_PROVIDER_IDENTITY_CHANGED);
        }

        if (userRepository.findByEmail(intent.email()).isPresent()) {
            throw new OAuth2AuthenticationException(MANUAL_LINK_REQUIRED);
        }
        if (!intent.emailVerified()) {
            throw new OAuth2AuthenticationException("oauth2_email_verification_required");
        }

        return saveNewUser(
                intent.email(),
                intent.userName(),
                intent.provider(),
                intent.providerId(),
                intent.profileImageUrl());
    }

    private UserEntity saveNewUser(String email, String name, String provider, String providerId,
            String profileImageUrl) {
        UserEntity userEntity = UserEntity.builder()
                .email(email)
                .name(name != null && !name.isEmpty() ? name : "소셜 사용자")
                .password(null)
                .profileImageUrl(profileImageUrl)
                .role("ROLE_USER")
                .provider(provider)
                .providerId(providerId)
                .favoriteTeam(null)
                .uniqueId(UUID.randomUUID())
                .handle(generateRandomHandle())
                .build();

        UserEntity savedUser = userRepository.save(Objects.requireNonNull(userEntity));
        linkAccount(Objects.requireNonNull(savedUser), provider, providerId, email);
        return savedUser;
    }

    private String generateRandomHandle() {
        String randomHex = UUID.randomUUID().toString().replace("-", "");
        return "u_" + randomHex.substring(0, 12);
    }

    private void linkAccount(UserEntity user, String provider, String providerId, String email) {
        Optional<UserProvider> providerSlot = userProviderRepository
                .findByUserIdAndProviderForUpdate(user.getId(), provider);
        if (providerSlot.isPresent()) {
            UserProvider current = providerSlot.get();
            if (!Objects.equals(current.getProviderId(), providerId)) {
                recordLinkConflict(provider, user.getId());
                throw new OAuth2AuthenticationException(OAUTH2_LINK_REQUIRES_UNLINK);
            }
            if (email != null && !email.equals(current.getEmail())) {
                current.setEmail(email);
                userProviderRepository.save(current);
            }
            return;
        }

        Optional<UserProvider> existingProvider = userProviderRepository
                .findByProviderAndProviderIdForUpdate(provider, providerId);
        if (existingProvider.isPresent()) {
            recordLinkConflict(provider, user.getId());
            throw new OAuth2AuthenticationException(OAUTH2_LINK_CONFLICT);
        }

        try {
            userProviderRepository.saveAndFlush(UserProvider.builder()
                    .user(user)
                    .provider(provider)
                    .providerId(providerId)
                    .email(email)
                    .build());
        } catch (DataIntegrityViolationException exception) {
            recordLinkConflict(provider, user.getId());
            throw new OAuth2AuthenticationException(OAUTH2_LINK_CONFLICT);
        }
    }

    private UserEntity processAccountLink(
            OAuth2LinkStateData linkData,
            String provider,
            String providerId,
            String email) {
        Long userId = linkData.userId();
        Optional<UserEntity> targetUser = userRepository.findByIdForWrite(userId);
        if (targetUser.isEmpty()) {
            throw new OAuth2AuthenticationException(OAUTH2_LINK_FAILED);
        }

        UserEntity user = targetUser.get();
        assertEligible(user);
        Optional<UserProvider> currentProvider = userProviderRepository
                .findByUserIdAndProviderForUpdate(userId, provider);
        if (currentProvider.isPresent()) {
            UserProvider currentLink = currentProvider.get();
            if (!Objects.equals(currentLink.getProviderId(), providerId)) {
                recordLinkConflict(provider, userId);
                throw new OAuth2AuthenticationException(OAUTH2_LINK_REQUIRES_UNLINK);
            }
            updateProviderEmail(currentLink, email);
            return user;
        }

        Optional<UserProvider> existingProvider = userProviderRepository
                .findByProviderAndProviderIdForUpdate(provider, providerId);
        if (existingProvider.isPresent()) {
            if (!Objects.equals(existingProvider.get().getUser().getId(), userId)) {
                recordLinkConflict(provider, userId);
                throw new OAuth2AuthenticationException(OAUTH2_LINK_CONFLICT);
            }
            updateProviderEmail(existingProvider.get(), email);
            return user;
        }

        try {
            userProviderRepository.saveAndFlush(UserProvider.builder()
                    .user(user)
                    .provider(provider)
                    .providerId(providerId)
                    .email(email)
                    .build());
            log.info("Account linked successfully for userId={}, provider={}", userId, provider);
            return user;
        } catch (DataIntegrityViolationException exception) {
            recordLinkConflict(provider, userId);
            throw new OAuth2AuthenticationException(OAUTH2_LINK_CONFLICT);
        }
    }

    private void updateProviderEmail(UserProvider provider, String email) {
        if (email != null && !email.equals(provider.getEmail())) {
            provider.setEmail(email);
            userProviderRepository.save(provider);
        }
    }

    private void recordLinkConflict(String provider, Long userId) {
        securityMonitoringService.recordOAuth2LinkConflict();
        log.warn("Rejected conflicting provider link attempt: provider={}, targetUserId={}", provider, userId);
    }

    private void updateUser(UserEntity user, String newName) {
        if (user.getName() == null || user.getName().isEmpty() || "소셜 사용자".equals(user.getName())) {
            if (newName != null && !newName.isEmpty()) {
                user.setName(newName);
                userRepository.save(user);
            }
        }
    }

    private void assertEligible(UserEntity user) {
        if (user == null || !user.isEnabled() || user.isPendingDeletion() || !AccountStatusUtil.isAccountUsable(user)) {
            throw new OAuth2AuthenticationException(OAUTH2_ACCOUNT_NOT_ELIGIBLE);
        }
    }

    private void applyOAuthDailyBonus(UserEntity user) {
        LocalDate today = LocalDate.now();
        if (user.getLastBonusDate() == null || !user.getLastBonusDate().equals(today)) {
            user.addCheerPoints(5);
            user.setLastBonusDate(today);
            log.info("Daily Login Bonus (5 points) awarded to Social User id={}", user.getId());
        }
    }

    private void applyProfileImageFromOAuth(UserEntity user, String profileImageUrl, String provider) {
        if (profileImageUrl == null) {
            return;
        }
        String currentProfileImageUrl = user.getProfileImageUrl();
        if (currentProfileImageUrl == null
                || shouldReplaceProfileImageFromOAuth(user, currentProfileImageUrl, profileImageUrl, provider)) {
            user.setProfileImageUrl(profileImageUrl);
            userRepository.save(user);
        }
    }

    private boolean shouldReplaceProfileImageFromOAuth(UserEntity user, String currentProfileImageUrl,
            String newProfileImageUrl, String provider) {
        if (newProfileImageUrl.equals(currentProfileImageUrl)) {
            return false;
        }
        if (!provider.equalsIgnoreCase(user.getProvider())) {
            return false;
        }
        return isProviderProfileImageUrl(currentProfileImageUrl, provider);
    }

    private boolean isProviderProfileImageUrl(String url, String provider) {
        if (url == null) {
            return false;
        }
        return switch (provider.toLowerCase()) {
            case "kakao" -> url.contains("kakaocdn.net");
            case "naver" -> url.contains("naver");
            case "google" -> url.contains("googleusercontent.com");
            default -> false;
        };
    }

}
