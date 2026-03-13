package com.example.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpResolverTest {

    @Test
    void untrustedProxyHeadersAreIgnoredByDefault() {
        ClientIpResolver resolver = new ClientIpResolver("", new MockEnvironment());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.10");
        request.addHeader("X-Forwarded-For", "203.0.113.10");

        assertThat(resolver.resolve(request)).isEqualTo("10.0.0.10");
    }

    @Test
    void trustedProxyUsesForwardedForHeader() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.10", new MockEnvironment());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.10");
        request.addHeader("X-Forwarded-For", "203.0.113.10, 10.0.0.10");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.10");
    }

    @Test
    void localProfileTrustsLoopbackProxyHeaders() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");
        ClientIpResolver resolver = new ClientIpResolver("", environment);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Real-IP", "198.51.100.7");

        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.7");
    }
}
