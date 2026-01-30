package com.example.auth.oauth2;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.SerializationUtils;
import java.util.Base64;

import java.util.Arrays;
import java.util.Optional;
import com.example.auth.util.JWTUtil;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class CookieAuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    private final JWTUtil jwtUtil;

    // 쿠키 이름 정의
    public static final String OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME = "oauth2_auth_request";
    public static final String REDIRECT_URI_PARAM_COOKIE_NAME = "redirect_uri";
    public static final String LINK_MODE_COOKIE_NAME = "oauth2_link_mode";
    public static final String LINK_USER_ID_COOKIE_NAME = "oauth2_link_user_id";
    // 쿠키 만료 시간 (초 단위)
    private static final int COOKIE_EXPIRE_SECONDS = 300; // 5분

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        // 쿠키에서 Authorization Request 정보를 로드합니다.
        return getCookie(request, OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME)
                .map(this::deserialize)
                .orElse(null);
    }

    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest, HttpServletRequest request,
            HttpServletResponse response) {
        if (authorizationRequest == null) {
            // 요청이 null이면 쿠키를 제거합니다.
            removeAuthorizationRequestCookies(request, response);
            return;
        }

        // 1. Authorization Request를 쿠키에 저장합니다.
        String serialized = serialize(authorizationRequest);
        addCookie(response, OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME, serialized, COOKIE_EXPIRE_SECONDS);

        // DEBUG: Check parameters
        String modeParam = request.getParameter("mode");
        String footerParam = request.getParameter("userId");
        log.debug("saveAuthorizationRequest - mode: {}, userId: {}", modeParam, footerParam);

        // 2. 리다이렉트 URI도 쿠키에 저장합니다 (CustomSuccessHandler에서 사용).
        String redirectUriAfterLogin = request.getParameter(REDIRECT_URI_PARAM_COOKIE_NAME);
        if (redirectUriAfterLogin != null && !redirectUriAfterLogin.isBlank()) {
            addCookie(response, REDIRECT_URI_PARAM_COOKIE_NAME, redirectUriAfterLogin, COOKIE_EXPIRE_SECONDS);
        }

        // 3. 계정 연동 모드 및 사용자 ID 저장 ('mode', 'userId' 파라미터) -> 쿠키에 저장
        String mode = request.getParameter("mode");
        if (mode != null && !mode.isBlank()) {
            addCookie(response, LINK_MODE_COOKIE_NAME, mode, COOKIE_EXPIRE_SECONDS);
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // [Security Fix] Trust only authenticated user for linking
        if (authentication != null && authentication.isAuthenticated() &&
                !"anonymousUser".equals(authentication.getPrincipal())) {

            Long authUserId = null;
            if (authentication.getPrincipal() instanceof com.example.auth.service.CustomUserDetails) {
                authUserId = ((com.example.auth.service.CustomUserDetails) authentication.getPrincipal()).getId();
            } else if (authentication.getPrincipal() instanceof com.example.auth.dto.CustomOAuth2User) {
                authUserId = ((com.example.auth.dto.CustomOAuth2User) authentication.getPrincipal()).getUserDto()
                        .getId();
            }

            if (authUserId != null) {
                // Sign the UserID into a short-lived JWT (5 min)
                String signedUserIdToken = jwtUtil.createJwt("link-action", "LINK_MODE", authUserId,
                        COOKIE_EXPIRE_SECONDS * 1000L);
                addCookie(response, LINK_USER_ID_COOKIE_NAME, signedUserIdToken, COOKIE_EXPIRE_SECONDS);
            }
        }
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
        // deleteCookie(request, response, LINK_MODE_COOKIE_NAME); //
        // CustomSuccessHandler에서 처리
        // deleteCookie(request, response, LINK_USER_ID_COOKIE_NAME); //
        // CustomSuccessHandler에서 처리
    }

    // --- 유틸리티 메서드 ---

    private void addCookie(HttpServletResponse response, String name, String value, int maxAgeSeconds) {
        org.springframework.http.ResponseCookie cookie = org.springframework.http.ResponseCookie.from(name, value)
                .path("/")
                .httpOnly(true)
                .maxAge(maxAgeSeconds)
                .sameSite("Lax")
                .build();
        response.addHeader(org.springframework.http.HttpHeaders.SET_COOKIE, cookie.toString());
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
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookie.getName().equals(name)) {
                    org.springframework.http.ResponseCookie expireCookie = org.springframework.http.ResponseCookie
                            .from(name, "")
                            .path("/")
                            .httpOnly(true)
                            .maxAge(0)
                            .sameSite("Lax")
                            .build();
                    response.addHeader(org.springframework.http.HttpHeaders.SET_COOKIE, expireCookie.toString());
                }
            }
        }
    }

    private String serialize(OAuth2AuthorizationRequest authorizationRequest) {
        byte[] bytes = SerializationUtils.serialize(authorizationRequest);
        return Base64.getUrlEncoder().encodeToString(bytes);
    }

    @SuppressWarnings("deprecation")
    private OAuth2AuthorizationRequest deserialize(Cookie cookie) {
        byte[] bytes = Base64.getUrlDecoder().decode(cookie.getValue());
        return (OAuth2AuthorizationRequest) SerializationUtils.deserialize(bytes);
    }
}