package com.example.auth.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.auth.service.AuthSecurityMonitoringService;
import com.example.common.ratelimit.RateLimitService;
import com.example.common.web.ClientIpResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    @Mock
    private RateLimitService rateLimitService;

    @Mock
    private ClientIpResolver clientIpResolver;

    @Mock
    private AuthSecurityMonitoringService authSecurityMonitoringService;

    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        lenient().when(clientIpResolver.resolveOrUnknown(any())).thenAnswer(invocation ->
                ((MockHttpServletRequest) invocation.getArgument(0)).getRemoteAddr());
        lenient().when(rateLimitService.isAllowed(anyString(), anyInt(), anyInt(), anyBoolean()))
                .thenReturn(true);
        filter = new RateLimitFilter(
                rateLimitService,
                clientIpResolver,
                authSecurityMonitoringService,
                new ObjectMapper());
    }

    @Test
    void login_usesRedisRateLimitKeyFromResolvedClientIp() throws Exception {
        MockHttpServletResponse response = doLoginRequest("203.0.113.10");

        assertThat(response.getStatus()).isEqualTo(200);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(rateLimitService).isAllowed(keyCaptor.capture(), eq(3), eq(60), eq(true));
        assertThat(keyCaptor.getValue())
                .isEqualTo("rate:limit:auth:login:POST:/api/auth/login:203.0.113.10");
    }

    @Test
    void passwordResetRequest_usesOneHourFailClosedRule() throws Exception {
        doRequest("POST", "/api/auth/password/reset/request", "203.0.113.20");

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(rateLimitService).isAllowed(keyCaptor.capture(), eq(3), eq(3600), eq(true));
        assertThat(keyCaptor.getValue())
                .isEqualTo("rate:limit:auth:password-reset-request:POST:/api/auth/password/reset/request:203.0.113.20");
    }

    @Test
    void rejectedRequest_returns429JsonAndRecordsMonitoringEvent() throws Exception {
        when(rateLimitService.isAllowed(anyString(), anyInt(), anyInt(), anyBoolean())).thenReturn(false);

        MockHttpServletResponse response = doLoginRequest("203.0.113.30");

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("60");
        assertThat(response.getContentAsString()).contains("RATE_LIMITED");
        assertThat(response.getContentAsString()).contains("retryAfterSeconds");
        verify(authSecurityMonitoringService).recordAuthRateLimitReject();
    }

    @Test
    void xForwardedForHeader_isNotTrustedDirectly() throws Exception {
        MockHttpServletRequest request = buildRequest("POST", "/api/auth/login", "203.0.113.40");
        request.addHeader("X-Forwarded-For", "198.51.100.77");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, newPassingChain());

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(rateLimitService).isAllowed(keyCaptor.capture(), eq(3), eq(60), eq(true));
        assertThat(keyCaptor.getValue()).contains("203.0.113.40");
        assertThat(keyCaptor.getValue()).doesNotContain("198.51.100.77");
    }

    @Test
    void nonRateLimitedEndpoint_isPassedThroughWithoutRedisCall() throws Exception {
        MockHttpServletResponse response = doRequest("GET", "/api/some/other/endpoint", "203.0.113.50");

        assertThat(response.getStatus()).isEqualTo(200);
        verifyNoInteractions(rateLimitService, authSecurityMonitoringService);
    }

    private MockHttpServletResponse doLoginRequest(String ip) throws Exception {
        return doRequest("POST", "/api/auth/login", ip);
    }

    private MockHttpServletResponse doRequest(String method, String path, String ip) throws Exception {
        MockHttpServletRequest request = buildRequest(method, path, ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, newPassingChain());
        return response;
    }

    private FilterChain newPassingChain() {
        return (request, response) -> ((MockHttpServletResponse) response).setStatus(200);
    }

    private MockHttpServletRequest buildRequest(String method, String path, String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod(method);
        request.setRequestURI(path);
        request.setRemoteAddr(ip);
        return request;
    }
}
