package com.example.auth.oauth2;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

import java.util.Collection;

@Component // Spring Bean으로 등록합니다.
public class NoJSessionIdAuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    // Spring Security의 기본 세션 기반 리포지토리를 내부적으로 사용합니다.
    private final HttpSessionOAuth2AuthorizationRequestRepository delegate = new HttpSessionOAuth2AuthorizationRequestRepository();

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        return this.delegate.loadAuthorizationRequest(request);
    }

    /**
     * OAuth2 인가 요청을 저장한 후, JSESSIONID 쿠키를 응답 헤더에서 제거합니다.
     */
    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest, HttpServletRequest request,
            HttpServletResponse response) {
        // 1. 기본 세션 기반 저장 로직 실행. (여기서 JSESSIONID가 response에 추가됨)
        this.delegate.saveAuthorizationRequest(authorizationRequest, request, response);

        // 2. JSESSIONID 쿠키를 응답에서 강제로 제거 (핵심 workaround)
        final String JSESSIONID = "JSESSIONID";

        // 응답 헤더에서 "JSESSIONID" 쿠키를 포함하는 Set-Cookie 헤더를 찾습니다.
        Collection<String> setCookieHeaders = response.getHeaders("Set-Cookie");
        if (setCookieHeaders != null && !setCookieHeaders.isEmpty()) {
            response.setHeader("Set-Cookie", null); // 기존 Set-Cookie 헤더를 모두 지웁니다.

            for (String header : setCookieHeaders) {
                if (header.contains(JSESSIONID)) {
                    // JSESSIONID 쿠키를 Max-Age=0 및 Expires로 즉시 만료시켜 제거합니다.
                    response.addHeader("Set-Cookie", JSESSIONID
                            + "=deleted; Path=/; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; HttpOnly");
                } else {
                    // JSESSIONID가 아닌 다른 쿠키(JWT 쿠키 등)는 다시 응답에 추가합니다.
                    response.addHeader("Set-Cookie", header);
                }
            }
        }
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request,
            HttpServletResponse response) {
        OAuth2AuthorizationRequest removedRequest = this.delegate.removeAuthorizationRequest(request, response);

        // 제거 후에도 혹시 남아있을 수 있는 JSESSIONID를 다시 한 번 무효화 처리
        response.addHeader("Set-Cookie",
                "JSESSIONID=deleted; Path=/; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; HttpOnly");

        return removedRequest;
    }
}