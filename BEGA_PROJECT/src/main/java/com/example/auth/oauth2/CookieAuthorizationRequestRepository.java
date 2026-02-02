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
import com.example.bega.auth.dto.OAuth2LinkStateData;
import com.example.bega.auth.service.OAuth2LinkStateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class CookieAuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    private final OAuth2LinkStateService oAuth2LinkStateService;
    private final com.example.auth.util.JWTUtil jwtUtil;

    // 쿠키 이름 정의
    public static final String OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME = "oauth2_auth_request";
    public static final String REDIRECT_URI_PARAM_COOKIE_NAME = "redirect_uri";
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

        // 1. 계정 연동 모드 확인
        String mode = request.getParameter("mode");
        String linkToken = request.getParameter("linkToken");
        String redirectUriParam = request.getParameter(REDIRECT_URI_PARAM_COOKIE_NAME);
        String originalState = authorizationRequest.getState();

        log.debug("saveAuthorizationRequest - mode: {}, linkToken: {}, state: {}",
                mode, linkToken != null ? "present" : "null", originalState);

        // 연동 모드일 경우 원본 state를 key로 사용하여 Redis에 저장
        if ("link".equals(mode)) {
            // [Security Fix] linkToken에서 userId 추출 (프론트엔드에서 사전에 발급받은 토큰)
            Long authUserId = null;
            String failureReason = null;

            if (linkToken != null) {
                try {
                    // 1. 토큰 만료 확인
                    if (jwtUtil.isExpired(linkToken)) {
                        failureReason = "LINK_TOKEN_EXPIRED";
                        log.warn("Link token expired");
                    } else {
                        // 2. 토큰 타입 확인 (link 타입만 허용)
                        String tokenType = jwtUtil.getTokenType(linkToken);
                        if (!"link".equals(tokenType)) {
                            // 하위 호환성: type claim이 없으면 category 확인 (legacy)
                            String category = jwtUtil.getEmail(linkToken);
                            if (!"link-action".equals(category)) {
                                failureReason = "INVALID_LINK_TOKEN_TYPE";
                                log.warn("Invalid link token type/category");
                            }
                        }
                    }

                    // 3. 사용자 ID 추출 (유효한 경우만)
                    if (failureReason == null) {
                        authUserId = jwtUtil.getUserId(linkToken);
                    }

                } catch (Exception e) {
                    failureReason = "INVALID_LINK_TOKEN";
                    log.warn("Failed to validate link token: {}", e.getMessage());
                }
            } else {
                failureReason = "MISSING_LINK_TOKEN";
                log.warn("Link mode requested but missing linkToken");
            }

            // Redis에 연동 정보(또는 실패 정보) 저장
            OAuth2LinkStateData linkData = new OAuth2LinkStateData(
                    mode,
                    authUserId,
                    redirectUriParam,
                    System.currentTimeMillis(),
                    failureReason // [Security Fix] 실패 사유 저장
            );

            // 원본 state를 key로 사용하여 저장
            oAuth2LinkStateService.saveLinkByState(originalState, linkData);
            log.info("Saved OAuth2 link state to Redis: state={}, userId={}, failure={}",
                    originalState, authUserId, failureReason);
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

        // 3. 리다이렉트 URI도 쿠키에 저장합니다 (CustomSuccessHandler에서 사용).
        if (redirectUriParam != null && !redirectUriParam.isBlank()) {
            addCookie(response, REDIRECT_URI_PARAM_COOKIE_NAME, redirectUriParam, COOKIE_EXPIRE_SECONDS);
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