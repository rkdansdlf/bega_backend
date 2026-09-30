package com.example.auth.service;

import com.example.auth.entity.OAuthEmailChallenge;
import com.example.auth.entity.UserEntity;
import com.example.auth.repository.OAuthEmailChallengeRepository;
import com.example.common.exception.BadRequestBusinessException;
import com.example.common.exception.RateLimitExceededException;
import com.example.common.ratelimit.RateLimitService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class OAuthEmailChallengeService {

    private static final int TOKEN_BYTES = 32;
    private static final int TTL_MINUTES = 30;

    private final OAuthEmailChallengeRepository repository;
    private final EmailService emailService;
    private final OAuthNewAccountService newAccountService;
    private final RateLimitService rateLimitService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public String issue(String provider, String providerId, String email, String name, String profileImageUrl) {
        repository.deleteByProviderAndProviderId(provider, providerId);
        LocalDateTime now = LocalDateTime.now();
        OAuthEmailChallenge challenge = repository.save(OAuthEmailChallenge.builder()
                .challengeId(UUID.randomUUID().toString())
                .provider(provider)
                .providerId(providerId)
                .name(name)
                .profileImageUrl(profileImageUrl)
                .createdAt(now)
                .status("EMAIL_REQUIRED")
                .used(false)
                .build());
        submitEmailInternal(challenge, email);
        return challenge.getChallengeId();
    }

    @Transactional
    public String issueEmailRequired(String provider, String providerId, String name, String profileImageUrl) {
        repository.deleteByProviderAndProviderId(provider, providerId);
        OAuthEmailChallenge challenge = repository.save(OAuthEmailChallenge.builder()
                .challengeId(UUID.randomUUID().toString())
                .provider(provider)
                .providerId(providerId)
                .name(name)
                .profileImageUrl(profileImageUrl)
                .createdAt(LocalDateTime.now())
                .status("EMAIL_REQUIRED")
                .used(false)
                .build());
        return challenge.getChallengeId();
    }

    @Transactional
    public void submitEmail(String challengeId, String email) {
        OAuthEmailChallenge challenge = findMutableChallenge(challengeId);
        submitEmailInternal(challenge, email);
    }

    @Transactional
    public void resend(String challengeId) {
        OAuthEmailChallenge challenge = findMutableChallenge(challengeId);
        if (!StringUtils.hasText(challenge.getEmail())) {
            throw new BadRequestBusinessException("OAUTH_EMAIL_REQUIRED", "확인할 이메일 주소를 먼저 입력해주세요.");
        }
        submitEmailInternal(challenge, challenge.getEmail());
    }

    @Transactional(readOnly = true)
    public ChallengeStatus status(String challengeId) {
        OAuthEmailChallenge challenge = repository.findByChallengeId(challengeId)
                .orElseThrow(() -> new BadRequestBusinessException(
                        "OAUTH_EMAIL_CHALLENGE_NOT_FOUND", "이메일 확인 요청을 찾을 수 없습니다."));
        String status = challenge.getStatus();
        if ("EMAIL_SENT".equals(status)
                && (challenge.getExpiryDate() == null || !challenge.getExpiryDate().isAfter(LocalDateTime.now()))) {
            status = "EXPIRED";
        }
        return new ChallengeStatus(
                challenge.getChallengeId(),
                status,
                maskEmail(challenge.getEmail()),
                challenge.getExpiryDate());
    }

    @Transactional
    public Long confirm(String rawToken) {
        if (!StringUtils.hasText(rawToken)) {
            throw new BadRequestBusinessException("INVALID_OAUTH_EMAIL_CHALLENGE", "유효하지 않은 이메일 확인 링크입니다.");
        }
        OAuthEmailChallenge challenge = repository.findByTokenDigestForUpdate(digest(rawToken))
                .orElseThrow(() -> new BadRequestBusinessException(
                        "INVALID_OAUTH_EMAIL_CHALLENGE", "유효하지 않은 이메일 확인 링크입니다."));
        if (challenge.isUsed()) {
            throw new BadRequestBusinessException("OAUTH_EMAIL_CHALLENGE_USED", "이미 사용된 이메일 확인 링크입니다.");
        }
        if (challenge.getExpiryDate() == null || !challenge.getExpiryDate().isAfter(LocalDateTime.now())) {
            throw new BadRequestBusinessException("OAUTH_EMAIL_CHALLENGE_EXPIRED", "만료된 이메일 확인 링크입니다.");
        }

        UserEntity user = newAccountService.create(
                challenge.getEmail(),
                challenge.getName(),
                challenge.getProvider(),
                challenge.getProviderId(),
                challenge.getProfileImageUrl());
        challenge.setUsed(true);
        challenge.setStatus("VERIFIED");
        challenge.setTokenDigest("used:" + UUID.randomUUID());
        repository.save(challenge);
        return user.getId();
    }

    private OAuthEmailChallenge findMutableChallenge(String challengeId) {
        OAuthEmailChallenge challenge = repository.findByChallengeIdForUpdate(challengeId)
                .orElseThrow(() -> new BadRequestBusinessException(
                        "OAUTH_EMAIL_CHALLENGE_NOT_FOUND", "이메일 확인 요청을 찾을 수 없습니다."));
        if (challenge.isUsed() || "VERIFIED".equals(challenge.getStatus())) {
            throw new BadRequestBusinessException("OAUTH_EMAIL_CHALLENGE_USED", "이미 완료된 이메일 확인 요청입니다.");
        }
        return challenge;
    }

    private void submitEmailInternal(OAuthEmailChallenge challenge, String email) {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase(java.util.Locale.ROOT);
        if (!StringUtils.hasText(normalizedEmail) || !normalizedEmail.contains("@") || normalizedEmail.length() > 320) {
            throw new BadRequestBusinessException("INVALID_OAUTH_EMAIL", "유효한 이메일 주소를 입력해주세요.");
        }
        String emailRateKey = "rate:limit:auth:oauth-email-challenge-email:" + digest(normalizedEmail);
        if (!rateLimitService.isAllowed(emailRateKey, 3, 3600, true)) {
            throw new RateLimitExceededException("이 이메일로 너무 많은 확인 요청을 보냈습니다. 잠시 후 다시 시도해주세요.");
        }
        String rawToken = generateToken();
        LocalDateTime now = LocalDateTime.now();
        challenge.setEmail(normalizedEmail);
        challenge.setTokenDigest(digest(rawToken));
        challenge.setExpiryDate(now.plusMinutes(TTL_MINUTES));
        challenge.setStatus("EMAIL_SENT");
        repository.save(challenge);
        emailService.sendOAuthEmailChallenge(normalizedEmail, challenge.getChallengeId(), rawToken);
    }

    private String maskEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return null;
        }
        int separator = email.indexOf('@');
        if (separator <= 1) {
            return "***" + email.substring(Math.max(0, separator));
        }
        return email.charAt(0) + "***" + email.substring(separator);
    }

    public record ChallengeStatus(
            String challengeId,
            String status,
            String maskedEmail,
            LocalDateTime expiresAt) {
    }

    static String digest(String token) {
        try {
            byte[] hashed = MessageDigest.getInstance("SHA-256")
                    .digest(String.valueOf(token).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
