package com.example.auth.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserRepository;
import com.example.common.exception.CaptchaRequiredException;
import com.example.common.exception.RateLimitExceededException;
import com.example.common.web.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class LoginAttemptServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ClientIpResolver clientIpResolver;

    @Mock
    private CaptchaVerifier captchaVerifier;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthSecurityMonitoringService authSecurityMonitoringService;

    @Mock
    private HttpServletRequest request;

    private LoginAttemptService loginAttemptService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(clientIpResolver.resolveOrUnknown(request)).thenReturn("203.0.113.10");
        loginAttemptService = new LoginAttemptService(
                redisTemplate,
                clientIpResolver,
                captchaVerifier,
                userRepository,
                authSecurityMonitoringService);
    }

    @Test
    void enforceBeforeAuthentication_rejectsWhenIpFailureLimitIsReached() {
        when(valueOperations.get(anyString())).thenReturn("3");

        assertThatThrownBy(() -> loginAttemptService.enforceBeforeAuthentication(
                "user@example.com", null, request))
                .isInstanceOf(RateLimitExceededException.class);

        verify(authSecurityMonitoringService).recordAuthRateLimitReject();
    }

    @Test
    void enforceBeforeAuthentication_rejectsWhenEmailFailureLimitIsReached() {
        when(valueOperations.get(anyString())).thenReturn("0", "5");

        assertThatThrownBy(() -> loginAttemptService.enforceBeforeAuthentication(
                "user@example.com", null, request))
                .isInstanceOf(RateLimitExceededException.class);

        verify(authSecurityMonitoringService).recordAuthRateLimitReject();
    }

    @Test
    void recordFailedAttempt_locksExistingUserWhenEmailFailureLimitIsReached() {
        UserEntity user = UserEntity.builder()
                .id(10L)
                .uniqueId(UUID.randomUUID())
                .email("user@example.com")
                .enabled(true)
                .locked(false)
                .build();
        when(valueOperations.increment(anyString())).thenReturn(1L, 1L, 5L);

        loginAttemptService.recordFailedAttempt("user@example.com", user, request);

        org.assertj.core.api.Assertions.assertThat(user.isLocked()).isTrue();
        org.assertj.core.api.Assertions.assertThat(user.getLockExpiresAt()).isNotNull();
        verify(userRepository).save(user);
    }

    @Test
    void recordSuccessfulLogin_clearsFailureKeys() {
        loginAttemptService.recordSuccessfulLogin("user@example.com", request);

        verify(redisTemplate).delete(org.mockito.ArgumentMatchers.startsWith("auth:login:fail:ip:"));
        verify(redisTemplate).delete(org.mockito.ArgumentMatchers.startsWith("auth:login:fail:captcha:"));
        verify(redisTemplate).delete(org.mockito.ArgumentMatchers.startsWith("auth:login:fail:email:"));
    }

    @Test
    void enforceBeforeAuthentication_requiresCaptchaAfterRepeatedFailuresWhenEnabled() {
        when(valueOperations.get(anyString())).thenReturn("0", "3");
        when(captchaVerifier.isEnabled()).thenReturn(true);
        when(captchaVerifier.verify(null, request)).thenReturn(false);

        assertThatThrownBy(() -> loginAttemptService.enforceBeforeAuthentication(
                "user@example.com", null, request))
                .isInstanceOf(CaptchaRequiredException.class);
    }
}
