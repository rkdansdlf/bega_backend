package com.example.auth.service;

import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserRepository;
import com.example.common.exception.CaptchaRequiredException;
import com.example.common.exception.RateLimitExceededException;
import com.example.common.web.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private static final int IP_FAILURE_LIMIT = 3;
    private static final Duration IP_WINDOW = Duration.ofMinutes(5);
    private static final int EMAIL_FAILURE_LIMIT = 5;
    private static final Duration EMAIL_WINDOW = Duration.ofHours(1);
    private static final int CAPTCHA_FAILURE_LIMIT = 3;
    private static final Duration CAPTCHA_WINDOW = Duration.ofMinutes(15);
    private static final Duration ACCOUNT_LOCK_DURATION = Duration.ofMinutes(15);

    private final StringRedisTemplate redisTemplate;
    private final ClientIpResolver clientIpResolver;
    private final CaptchaVerifier captchaVerifier;
    private final UserRepository userRepository;
    private final AuthSecurityMonitoringService authSecurityMonitoringService;

    public void enforceBeforeAuthentication(String normalizedEmail, String captchaToken, HttpServletRequest request) {
        String ipKey = ipFailureKey(request);
        if (getCount(ipKey) >= IP_FAILURE_LIMIT) {
            authSecurityMonitoringService.recordAuthRateLimitReject();
            throw new RateLimitExceededException("요청이 너무 많습니다. 잠시 후 다시 시도해주세요.");
        }

        if (StringUtils.hasText(normalizedEmail)
                && getCount(emailFailureKey(normalizedEmail)) >= EMAIL_FAILURE_LIMIT) {
            authSecurityMonitoringService.recordAuthRateLimitReject();
            throw new RateLimitExceededException("요청이 너무 많습니다. 잠시 후 다시 시도해주세요.");
        }

        if (captchaVerifier.isEnabled()
                && getCount(captchaFailureKey(normalizedEmail, request)) >= CAPTCHA_FAILURE_LIMIT
                && !captchaVerifier.verify(captchaToken, request)) {
            throw new CaptchaRequiredException();
        }
    }

    public void recordFailedAttempt(String normalizedEmail, UserEntity user, HttpServletRequest request) {
        long ipFailures = increment(ipFailureKey(request), IP_WINDOW);
        increment(captchaFailureKey(normalizedEmail, request), CAPTCHA_WINDOW);

        if (StringUtils.hasText(normalizedEmail)) {
            long emailFailures = increment(emailFailureKey(normalizedEmail), EMAIL_WINDOW);
            if (user != null && emailFailures >= EMAIL_FAILURE_LIMIT) {
                user.setLocked(true);
                user.setLockExpiresAt(LocalDateTime.now().plus(ACCOUNT_LOCK_DURATION));
                userRepository.save(user);
                log.warn("Account temporarily locked after repeated login failures: userId={}", user.getId());
            }
        }

        if (ipFailures >= IP_FAILURE_LIMIT) {
            authSecurityMonitoringService.recordAuthRateLimitReject();
            throw new RateLimitExceededException("요청이 너무 많습니다. 잠시 후 다시 시도해주세요.");
        }
    }

    public void recordSuccessfulLogin(String normalizedEmail, HttpServletRequest request) {
        delete(ipFailureKey(request));
        delete(captchaFailureKey(normalizedEmail, request));
        if (StringUtils.hasText(normalizedEmail)) {
            delete(emailFailureKey(normalizedEmail));
        }
    }

    private long increment(String key, Duration ttl) {
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            redisTemplate.expire(key, ttl);
            return count == null ? 0L : count;
        } catch (RuntimeException e) {
            log.error("Failed to update login failure counter: key={}", key, e);
            authSecurityMonitoringService.recordAuthRateLimitReject();
            throw new RateLimitExceededException("요청이 너무 많습니다. 잠시 후 다시 시도해주세요.");
        }
    }

    private long getCount(String key) {
        try {
            String value = redisTemplate.opsForValue().get(key);
            if (!StringUtils.hasText(value)) {
                return 0L;
            }
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            delete(key);
            return 0L;
        } catch (RuntimeException e) {
            log.error("Failed to read login failure counter: key={}", key, e);
            authSecurityMonitoringService.recordAuthRateLimitReject();
            throw new RateLimitExceededException("요청이 너무 많습니다. 잠시 후 다시 시도해주세요.");
        }
    }

    private void delete(String key) {
        try {
            redisTemplate.delete(key);
        } catch (RuntimeException e) {
            log.warn("Failed to delete login failure counter: key={}", key, e);
        }
    }

    private String ipFailureKey(HttpServletRequest request) {
        return "auth:login:fail:ip:" + digest(clientIpResolver.resolveOrUnknown(request));
    }

    private String emailFailureKey(String normalizedEmail) {
        return "auth:login:fail:email:" + digest(normalizedEmail);
    }

    private String captchaFailureKey(String normalizedEmail, HttpServletRequest request) {
        String subject = StringUtils.hasText(normalizedEmail) ? normalizedEmail : clientIpResolver.resolveOrUnknown(request);
        return "auth:login:fail:captcha:" + digest(subject);
    }

    private String digest(String value) {
        try {
            byte[] hashed = MessageDigest.getInstance("SHA-256")
                    .digest(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
