package com.example.common.ratelimit;

import com.example.common.exception.RateLimitExceededException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    private final RateLimitService rateLimitService;

    @Before("@annotation(com.example.common.ratelimit.RateLimit)")
    public void checkRateLimit(JoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RateLimit annotation = method.getAnnotation(RateLimit.class);

        String key = generateKey(joinPoint, annotation);
        int limit = annotation.limit();
        int window = annotation.window();

        if (!rateLimitService.isAllowed(key, limit, window)) {
            log.warn("Rate limit exceeded for key: {}", key);
            throw new RateLimitExceededException("너무 많은 요청을 보냈습니다. 잠시 후 다시 시도해주세요.");
        }
    }

    private String generateKey(JoinPoint joinPoint, RateLimit annotation) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return "rate:limit:unknown";
        }
        HttpServletRequest request = attributes.getRequest();

        // 1. 엔드포인트 정보
        String endpoint = joinPoint.getSignature().toShortString();

        // 2. 식별자 정보 (로그인 시 ID, 비로그인 시 IP)
        String clientIdentifier;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.isAuthenticated() && !auth.getPrincipal().equals("anonymousUser")) {
            clientIdentifier = auth.getName(); // Email or Username
        } else {
            clientIdentifier = getClientIP(request);
        }

        // 3. 사용자 정의 키가 있으면 추가
        String customKey = annotation.key();
        if (!customKey.isEmpty()) {
            return String.format("rate:limit:%s:%s:%s", customKey, endpoint, clientIdentifier);
        }

        return String.format("rate:limit:%s:%s", endpoint, clientIdentifier);
    }

    private String getClientIP(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0].trim();
    }
}
