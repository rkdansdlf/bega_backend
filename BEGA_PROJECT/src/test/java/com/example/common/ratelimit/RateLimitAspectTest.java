package com.example.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.common.exception.RateLimitExceededException;
import com.example.common.web.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class RateLimitAspectTest {

    private final RateLimitService rateLimitService = mock(RateLimitService.class);
    private final ClientIpResolver clientIpResolver = mock(ClientIpResolver.class);
    private final AuthRateLimitSecurityEventReporter reporter = mock(AuthRateLimitSecurityEventReporter.class);

    @BeforeEach
    void setUpRequest() {
        HttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        SecurityContextHolder.clearContext();
        when(clientIpResolver.resolveOrUnknown(request)).thenReturn("127.0.0.1");
        when(rateLimitService.isAllowed(anyString(), anyInt(), anyInt(), anyBoolean())).thenReturn(false);
    }

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectedAuthLimitReportsSecurityEvent() throws Exception {
        RateLimitAspect aspect = new RateLimitAspect(rateLimitService, clientIpResolver, reporter);

        assertThatThrownBy(() -> aspect.checkRateLimit(joinPointFor("authLimited")))
                .isInstanceOf(RateLimitExceededException.class);

        verify(reporter).recordRejected();
    }

    @Test
    void rejectedNonAuthLimitDoesNotReportAuthSecurityEvent() throws Exception {
        RateLimitAspect aspect = new RateLimitAspect(rateLimitService, clientIpResolver, reporter);

        assertThatThrownBy(() -> aspect.checkRateLimit(joinPointFor("generalLimited")))
                .isInstanceOf(RateLimitExceededException.class);

        verify(reporter, never()).recordRejected();
    }

    @Test
    void reporterFailureDoesNotReplaceRateLimitException() throws Exception {
        AuthRateLimitSecurityEventReporter failingReporter = () -> {
            throw new IllegalStateException("metrics unavailable");
        };
        RateLimitAspect aspect = new RateLimitAspect(rateLimitService, clientIpResolver, failingReporter);

        assertThatThrownBy(() -> aspect.checkRateLimit(joinPointFor("authLimited")))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void missingReporterDoesNotDisableRateLimitEnforcement() throws Exception {
        RateLimitAspect aspect = new RateLimitAspect(rateLimitService, clientIpResolver, null);

        assertThatThrownBy(() -> aspect.checkRateLimit(joinPointFor("authLimited")))
                .isInstanceOf(RateLimitExceededException.class);
    }

    private JoinPoint joinPointFor(String methodName) throws Exception {
        Method method = Fixture.class.getDeclaredMethod(methodName);
        MethodSignature signature = mock(MethodSignature.class);
        JoinPoint joinPoint = mock(JoinPoint.class);
        when(signature.getMethod()).thenReturn(method);
        when(signature.toShortString()).thenReturn("Fixture." + methodName + "()");
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getTarget()).thenReturn(new Fixture());
        return joinPoint;
    }

    private static final class Fixture {

        @RateLimit(limit = 1, window = 60, key = "auth:test")
        void authLimited() {
        }

        @RateLimit(limit = 1, window = 60, key = "general:test")
        void generalLimited() {
        }
    }
}
