package com.example.auth.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * [Security Fix - High #1] RateLimitFilter 단위 테스트.
 * Bucket 고갈 시 429 응답과 Retry-After 헤더를 확인한다.
 */
class RateLimitFilterTest {

    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter();
    }

    @Test
    void login_allowsFirst5Requests_thenReturns429() throws Exception {
        for (int i = 0; i < 5; i++) {
            MockHttpServletResponse response = doLoginRequest("10.0.0.1");
            assertThat(response.getStatus()).isEqualTo(200);
        }
        MockHttpServletResponse sixth = doLoginRequest("10.0.0.1");
        assertThat(sixth.getStatus()).isEqualTo(429);
        assertThat(sixth.getHeader("Retry-After")).isNotNull();
        assertThat(sixth.getContentAsString()).contains("RATE_LIMITED");
    }

    @Test
    void signup_allowsFirst3Requests_thenReturns429() throws Exception {
        for (int i = 0; i < 3; i++) {
            MockHttpServletResponse response = doRequest("POST", "/api/auth/signup", "10.0.0.2");
            assertThat(response.getStatus()).isEqualTo(200);
        }
        MockHttpServletResponse fourth = doRequest("POST", "/api/auth/signup", "10.0.0.2");
        assertThat(fourth.getStatus()).isEqualTo(429);
    }

    @Test
    void nonRateLimitedEndpoint_isAlwaysPassedThrough() throws Exception {
        for (int i = 0; i < 20; i++) {
            MockHttpServletResponse response = doRequest("GET", "/api/some/other/endpoint", "10.0.0.3");
            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    @Test
    void rateLimitIsIsolatedPerIp() throws Exception {
        for (int i = 0; i < 5; i++) {
            doLoginRequest("10.0.0.4");
        }
        MockHttpServletResponse blocked = doLoginRequest("10.0.0.4");
        assertThat(blocked.getStatus()).isEqualTo(429);

        MockHttpServletResponse allowed = doLoginRequest("10.0.0.5");
        assertThat(allowed.getStatus()).isEqualTo(200);
    }

    @Test
    void xForwardedForHeader_isUsedAsClientIp() throws Exception {
        for (int i = 0; i < 5; i++) {
            MockHttpServletRequest request = buildRequest("POST", "/api/auth/login", "10.0.0.100");
            request.addHeader("X-Forwarded-For", "192.168.99.1, 10.0.0.7");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
        }
        // 같은 X-Forwarded-For 앞단 IP 사용자는 차단됨
        MockHttpServletRequest blockedReq = buildRequest("POST", "/api/auth/login", "10.0.0.200");
        blockedReq.addHeader("X-Forwarded-For", "192.168.99.1, 10.0.0.8");
        MockHttpServletResponse blockedRes = new MockHttpServletResponse();
        filter.doFilter(blockedReq, blockedRes, new MockFilterChain());
        assertThat(blockedRes.getStatus()).isEqualTo(429);
    }

    @Test
    void resetBuckets_clearsAllQuotas() throws Exception {
        for (int i = 0; i < 5; i++) {
            doLoginRequest("10.0.0.9");
        }
        assertThat(doLoginRequest("10.0.0.9").getStatus()).isEqualTo(429);

        filter.resetBuckets();

        assertThat(doLoginRequest("10.0.0.9").getStatus()).isEqualTo(200);
    }

    private MockHttpServletResponse doLoginRequest(String ip) throws Exception {
        return doRequest("POST", "/api/auth/login", ip);
    }

    private MockHttpServletResponse doRequest(String method, String path, String ip) throws Exception {
        MockHttpServletRequest request = buildRequest(method, path, ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        return response;
    }

    private MockHttpServletRequest buildRequest(String method, String path, String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod(method);
        request.setRequestURI(path);
        request.setRemoteAddr(ip);
        return request;
    }
}
