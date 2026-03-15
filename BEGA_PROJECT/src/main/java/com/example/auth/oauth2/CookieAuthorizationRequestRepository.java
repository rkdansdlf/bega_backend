package com.example.auth.oauth2;

import com.example.bega.auth.dto.OAuth2LinkStateData;
import com.example.bega.auth.service.OAuth2LinkStateService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.SerializationUtils;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Component
public class CookieAuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    private static final String SIGNATURE_SEPARATOR = ".";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    public static final String OAUTH2_COOKIE_ERROR_ATTRIBUTE = "oauth2_auth_request_cookie_error";

    private final OAuth2LinkStateService oAuth2LinkStateService;
    private final String oauth2CookieSecret;
    private final boolean secureCookie;
    private final com.example.auth.service.AuthSecurityMonitoringService securityMonitoringService;

    // 쿠키 이름 정의
    public static final String OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME = "oauth2_auth_request";
    public static final String REDIRECT_URI_PARAM_COOKIE_NAME = "redirect_uri";
    // 쿠키 만료 시간 (초 단위)
    private static final int COOKIE_EXPIRE_SECONDS = 300; // 5분

    public CookieAuthorizationRequestRepository(
            OAuth2LinkStateService oAuth2LinkStateService,
            @Value("${app.oauth2.cookie-secret:}") String oauth2CookieSecret,
            @Value("${app.cookie.secure:false}") boolean secureCookie,
            com.example.auth.service.AuthSecurityMonitoringService securityMonitoringService) {
        this.oAuth2LinkStateService = oAuth2LinkStateService;
        this.oauth2CookieSecret = oauth2CookieSecret;
        this.secureCookie = secureCookie;
        this.securityMonitoringService = securityMonitoringService;
    }

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        Optional<Cookie> cookie = getCookie(request, OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME);
        if (cookie.isEmpty()) {
            return null;
        }

        try {
            return deserialize(cookie.get());
        } catch (IllegalArgumentException e) {
            securityMonitoringService.recordUnsignedOauth2Cookie();
            log.warn("Invalid OAuth2 request cookie received: {}", e.getMessage());
            request.setAttribute(OAUTH2_COOKIE_ERROR_ATTRIBUTE, "invalid_oauth2_request_cookie");
            return null;
        }
    }

    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest, HttpServletRequest request,
            HttpServletResponse response) {
        if (authorizationRequest == null) {
            // 요청이 null이면 쿠키를 제거합니다.
            removeAuthorizationRequestCookies(request, response);
            return;
        }

        // 1. 계정 연동 모드 확인
        String mode = request.getParameter("mode");
        String linkToken = request.getParameter("linkToken");
        String originalState = authorizationRequest.getState();

        log.debug("saveAuthorizationRequest - mode: {}, linkToken: {}, state: {}",
                mode, linkToken != null ? "present" : "null", originalState);

        // 연동 모드일 경우 원본 state를 key로 사용하여 Redis에 저장
        if ("link".equals(mode)) {
            Long authUserId = null;
            String failureReason = null;
            try {
                OAuth2LinkStateService.LinkTicketConsumeResult consumeResult = oAuth2LinkStateService
                        .consumeLinkToken(linkToken);
                authUserId = consumeResult.userId();
                failureReason = consumeResult.failureReason();
            } catch (OAuth2LinkStateService.OAuth2LinkStateStoreException e) {
                securityMonitoringService.recordOAuth2LinkReject();
                log.error("Failed to consume OAuth2 link ticket", e);
            }

            // Redis에 연동 정보(또는 실패 정보) 저장
            OAuth2LinkStateData linkData = new OAuth2LinkStateData(
                    authUserId,
                    System.currentTimeMillis(),
                    failureReason // [Security Fix] 실패 사유 저장
            );

            // 원본 state를 key로 사용하여 저장
            try {
                oAuth2LinkStateService.saveLinkByState(originalState, linkData);
                log.info("Saved OAuth2 link state to Redis: userId={}, failure={}",
                        authUserId, failureReason);
            } catch (OAuth2LinkStateService.OAuth2LinkStateStoreException e) {
                securityMonitoringService.recordOAuth2LinkReject();
                log.error("Failed to save OAuth2 link state; link flow will be rejected later", e);
            }
        }

        // 2. Authorization Request를 쿠키에 저장합니다 (state 수정 없이 원본 그대로).
        // [Strict Mode] Link 모드인 경우 Request Attribute에 표기하여 Cookie에 저장
        // (Redis 데이터가 만료되더라도 요청 의도를 파악하기 위함)
        OAuth2AuthorizationRequest requestToSave = authorizationRequest;
        if ("link".equals(mode)) {
            requestToSave = OAuth2AuthorizationRequest.from(authorizationRequest)
                    .attributes(attrs -> attrs.put("mode", "link"))
                    .build();
        }

        String serialized = serialize(requestToSave);
        addCookie(response, OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME, serialized, COOKIE_EXPIRE_SECONDS);
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request,
            HttpServletResponse response) {
        OAuth2AuthorizationRequest originalRequest = this.loadAuthorizationRequest(request);
        removeAuthorizationRequestCookies(request, response);
        return originalRequest;
    }

    /**
     * 관련 쿠키를 모두 제거합니다.
     */
    public void removeAuthorizationRequestCookies(HttpServletRequest request, HttpServletResponse response) {
        deleteCookie(request, response, OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME);
        deleteCookie(request, response, REDIRECT_URI_PARAM_COOKIE_NAME);
    }

    // --- 유틸리티 메서드 ---

    private void addCookie(HttpServletResponse response, String name, String value, int maxAgeSeconds) {
        if (value == null) {
            value = "";
        }

        ResponseCookie cookie = ResponseCookie.from(Objects.requireNonNull(name), value)
                .path("/")
                .httpOnly(true)
                .secure(secureCookie)
                .maxAge(maxAgeSeconds)
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private Optional<Cookie> getCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            return Arrays.stream(cookies)
                    .filter(cookie -> cookie.getName().equals(name))
                    .findFirst();
        }
        return Optional.empty();
    }

    private void deleteCookie(HttpServletRequest request, HttpServletResponse response, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return;
        }

        for (Cookie cookie : cookies) {
            if (cookie.getName().equals(name)) {
                ResponseCookie expireCookie = ResponseCookie.from(Objects.requireNonNull(name), "")
                        .path("/")
                        .httpOnly(true)
                        .secure(secureCookie)
                        .maxAge(0)
                        .sameSite("Lax")
                        .build();
                response.addHeader(HttpHeaders.SET_COOKIE, expireCookie.toString());
            }
        }
    }

    private String serialize(OAuth2AuthorizationRequest authorizationRequest) {
        byte[] payload = SerializationUtils.serialize(authorizationRequest);
        String encodedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(payload);
        return encodedPayload + SIGNATURE_SEPARATOR + sign(encodedPayload);
    }

    @SuppressWarnings("deprecation")
    private OAuth2AuthorizationRequest deserialize(Cookie cookie) {
        String rawValue = cookie.getValue();
        String[] parts = rawValue.split("\\.", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Malformed OAuth2 request cookie");
        }

        String encodedPayload = parts[0];
        String signature = parts[1];

        if (!isValidSignature(encodedPayload, signature)) {
            throw new IllegalArgumentException("Invalid OAuth2 request cookie signature");
        }

        try {
            byte[] bytes = Base64.getUrlDecoder().decode(encodedPayload);
            OAuth2AuthorizationRequest request = (OAuth2AuthorizationRequest) SerializationUtils
                    .deserialize(bytes);

            if (request == null) {
                throw new IllegalArgumentException("Deserialized OAuth2 request is null");
            }

            return request;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to deserialize OAuth2 request cookie", e);
        }
    }

    private boolean isValidSignature(String encodedPayload, String signature) {
        if (oauth2CookieSecret == null || oauth2CookieSecret.isBlank()) {
            log.error("OAuth2 cookie secret is not configured");
            return false;
        }

        String expectedSignature = sign(encodedPayload);
        if (signature == null || signature.isBlank() || signature.length() != expectedSignature.length()) {
            return false;
        }

        byte[] expected = Base64.getUrlDecoder().decode(expectedSignature);
        byte[] actual = Base64.getUrlDecoder().decode(signature);
        return MessageDigest.isEqual(expected, actual);
    }

    private String sign(String encodedPayload) {
        if (oauth2CookieSecret == null || oauth2CookieSecret.isBlank()) {
            return "";
        }

        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec secretKey = new SecretKeySpec(
                    oauth2CookieSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            mac.init(secretKey);
            byte[] digest = mac.doFinal(encodedPayload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception e) {
            log.error("Failed to sign OAuth2 request cookie", e);
            return "";
        }
    }
}
