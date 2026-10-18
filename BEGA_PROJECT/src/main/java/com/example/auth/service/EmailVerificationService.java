package com.example.auth.service;

import com.example.auth.entity.EmailVerificationToken;
import com.example.auth.entity.UserEntity;
import com.example.auth.repository.EmailVerificationTokenRepository;
import com.example.auth.repository.UserRepository;
import com.example.auth.util.LogMaskingUtil;
import com.example.common.exception.BadRequestBusinessException;
import com.example.common.exception.RateLimitExceededException;
import com.example.common.ratelimit.RateLimitService;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

/**
 * 이메일 회원가입 인증 (Issue #48).
 *
 * <ul>
 *   <li>가입 직후 1회용 링크 토큰을 발급해 메일로 보낸다(DB 에는 SHA-256 digest 만 저장).</li>
 *   <li>링크 확인 시 계정을 인증 완료로 바꾼다. 인증 전에는 비밀번호 로그인이 차단된다.</li>
 *   <li>재발송은 계정 존재 여부를 드러내지 않도록 항상 같은 응답을 준다(메일 발송 실패도 삼킨다).</li>
 *   <li>메일은 토큰을 저장한 트랜잭션이 커밋된 뒤에만 보낸다. 롤백되면 보내지 않는다.</li>
 * </ul>
 */
@Slf4j
@Service
public class EmailVerificationService {

    static final int TOKEN_BYTES = 32;
    static final int RESEND_LIMIT_PER_EMAIL = 3;
    static final int RESEND_WINDOW_SECONDS = 3600;

    private final EmailVerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final RateLimitService rateLimitService;
    private final boolean verificationRequired;
    private final long tokenTtlMinutes;
    private final boolean mailEnabled;
    private final SecureRandom secureRandom = new SecureRandom();

    public EmailVerificationService(
            EmailVerificationTokenRepository tokenRepository,
            UserRepository userRepository,
            EmailService emailService,
            RateLimitService rateLimitService,
            @Value("${app.auth.email-verification.required:false}") boolean verificationRequired,
            @Value("${app.auth.email-verification.token-ttl-minutes:1440}") long tokenTtlMinutes,
            @Value("${app.mail.enabled:false}") boolean mailEnabled) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.rateLimitService = rateLimitService;
        this.verificationRequired = verificationRequired;
        this.tokenTtlMinutes = tokenTtlMinutes > 0 ? tokenTtlMinutes : 1440;
        this.mailEnabled = mailEnabled;
    }

    @PostConstruct
    void logMode() {
        if (verificationRequired && !mailEnabled) {
            log.warn("Email verification is required but mail delivery is disabled (APP_MAIL_ENABLED=false). "
                    + "New email signups will not receive verification links and cannot log in.");
        }
        if (!verificationRequired) {
            log.info("Email verification is NOT enforced at login (APP_AUTH_EMAIL_VERIFICATION_REQUIRED=false, default). "
                    + "Verification mails are still sent and recorded.");
        }
    }

    public boolean isVerificationRequired() {
        return verificationRequired;
    }

    /**
     * 신규 이메일 가입 계정에 인증 메일을 보낸다. 메일은 가입 트랜잭션이 커밋된 뒤에 보내며,
     * 발송 실패는 가입 자체를 실패시키지 않는다(사용자는 재발송 API 로 다시 받을 수 있다).
     */
    @Transactional
    public void issueForSignup(UserEntity user) {
        if (user == null || user.getId() == null || user.isEmailVerified()) {
            return;
        }
        String rawToken = issueToken(user);
        sendAfterCommit(user.getId(), user.getEmail(), rawToken);
    }

    /**
     * 인증 메일 재발송. 계정이 없거나 이미 인증됐거나 소셜 전용이면 조용히 무시한다.
     */
    @Transactional
    public void resend(String email) {
        String normalizedEmail = normalizeEmail(email);
        if (!StringUtils.hasText(normalizedEmail)) {
            return;
        }
        String rateKey = "rate:limit:auth:email-verification-resend-email:" + digest(normalizedEmail);
        if (!rateLimitService.isAllowed(rateKey, RESEND_LIMIT_PER_EMAIL, RESEND_WINDOW_SECONDS, true)) {
            throw new RateLimitExceededException("인증 메일 재발송 요청이 너무 많습니다. 잠시 후 다시 시도해주세요.");
        }

        Optional<UserEntity> userOpt = userRepository.findByEmail(normalizedEmail);
        if (userOpt.isEmpty()) {
            log.info("Email verification resend suppressed for unknown email");
            return;
        }
        UserEntity user = userOpt.get();
        if (user.isEmailVerified() || user.getPassword() == null || !user.isEnabled() || user.isPendingDeletion()) {
            log.info("Email verification resend suppressed for userId={}", user.getId());
            return;
        }
        String rawToken = issueToken(user);
        // 발송 실패(SMTP/JobRunr)는 sendAfterCommit 안에서 로그만 남긴다: 응답이 계정 존재 여부를 드러내지 않게 한다.
        sendAfterCommit(user.getId(), user.getEmail(), rawToken);
    }

    /**
     * 인증 링크 확인. 성공하면 계정을 인증 완료로 바꾸고 남은 토큰을 지운다.
     */
    @Transactional
    public UserEntity confirm(String rawToken) {
        if (!StringUtils.hasText(rawToken)) {
            throw invalidToken();
        }
        EmailVerificationToken token = tokenRepository.findByTokenDigestForUpdate(digest(rawToken.trim()))
                .orElseThrow(EmailVerificationService::invalidToken);
        if (token.isUsed()) {
            throw new BadRequestBusinessException(
                    "EMAIL_VERIFICATION_TOKEN_USED", "이미 사용된 인증 링크입니다. 로그인해주세요.");
        }
        LocalDateTime now = LocalDateTime.now();
        if (token.isExpired(now)) {
            throw new BadRequestBusinessException(
                    "EMAIL_VERIFICATION_TOKEN_EXPIRED", "만료된 인증 링크입니다. 인증 메일을 다시 요청해주세요.");
        }

        UserEntity user = token.getUser();
        if (user == null || !user.isEnabled() || user.isPendingDeletion()) {
            // 비활성/탈퇴 예약 계정은 인증 완료로 바꾸지 않는다(상태를 드러내지 않도록 일반 무효 토큰 응답).
            log.info("Email verification rejected for inactive account userId={}", user == null ? null : user.getId());
            throw invalidToken();
        }
        if (user.isEmailVerified()) {
            // 다른 경로(예: 비밀번호 재설정)로 이미 인증된 계정의 남은 토큰. 성공(200)이 아니라 "이미 인증됨" 으로 안내한다.
            throw new BadRequestBusinessException(
                    "EMAIL_VERIFICATION_TOKEN_USED", "이미 인증된 계정입니다. 로그인해주세요.");
        }
        user.markEmailVerified(now);
        userRepository.save(user);

        // 같은 링크를 다시 열면 "이미 사용됨" 을 알려주도록 이 토큰은 used 로 남기고, 나머지 토큰만 지운다.
        token.setUsed(true);
        tokenRepository.save(token);
        tokenRepository.deleteOtherTokensOfUser(user.getId(), token.getId());
        log.info("Email verified for userId={}", user.getId());
        return user;
    }

    /**
     * 트랜잭션이 커밋된 뒤에 인증 메일을 보낸다. 트랜잭션이 없으면 바로 보낸다.
     * afterCommit 에서 던진 예외는 호출자에게 전파되므로 발송 예외는 여기서 모두 삼킨다.
     */
    private void sendAfterCommit(Long userId, String email, String rawToken) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sendQuietly(userId, email, rawToken);
                }
            });
            return;
        }
        sendQuietly(userId, email, rawToken);
    }

    private void sendQuietly(Long userId, String email, String rawToken) {
        try {
            emailService.sendSignupVerificationEmail(email, rawToken, tokenTtlMinutes);
        } catch (RuntimeException e) {
            log.warn("Email verification mail could not be sent for userId={}", userId, e);
        }
    }

    private String issueToken(UserEntity user) {
        tokenRepository.deleteAllByUserId(user.getId());
        String rawToken = generateToken();
        LocalDateTime now = LocalDateTime.now();
        tokenRepository.save(Objects.requireNonNull(EmailVerificationToken.builder()
                .user(user)
                .tokenDigest(digest(rawToken))
                .createdAt(now)
                .expiryDate(now.plusMinutes(tokenTtlMinutes))
                .used(false)
                .build()));
        log.info("Email verification issued for {}", LogMaskingUtil.maskEmail(user.getEmail()));
        return rawToken;
    }

    private static BadRequestBusinessException invalidToken() {
        return new BadRequestBusinessException(
                "INVALID_EMAIL_VERIFICATION_TOKEN", "유효하지 않은 인증 링크입니다.");
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
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
