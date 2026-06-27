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

    @Test
    void trustedCidrProxyUsesRealIpHeader() {
        ClientIpResolver resolver = new ClientIpResolver("172.16.0.0/12", new MockEnvironment());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("172.18.0.5");
        request.addHeader("X-Real-IP", "203.0.113.42");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.42");
    }

    @Test
    void remoteAddrOutsideTrustedCidrIgnoresForwardedHeaders() {
        ClientIpResolver resolver = new ClientIpResolver("172.16.0.0/12", new MockEnvironment());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.99");
        request.addHeader("X-Forwarded-For", "10.0.0.1");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.99");
    }

    @Test
    void mixedExactAndCidrTrustedEntriesAreBothHonored() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.10, 172.16.0.0/12", new MockEnvironment());

        MockHttpServletRequest exactReq = new MockHttpServletRequest();
        exactReq.setRemoteAddr("10.0.0.10");
        exactReq.addHeader("X-Forwarded-For", "203.0.113.10");
        assertThat(resolver.resolve(exactReq)).isEqualTo("203.0.113.10");

        MockHttpServletRequest cidrReq = new MockHttpServletRequest();
        cidrReq.setRemoteAddr("172.20.1.2");
        cidrReq.addHeader("X-Forwarded-For", "198.51.100.7");
        assertThat(resolver.resolve(cidrReq)).isEqualTo("198.51.100.7");
    }

    @Test
    void malformedCidrEntryIsIgnoredWithoutFailing() {
        ClientIpResolver resolver = new ClientIpResolver("not-a-cidr/99, 10.0.0.10", new MockEnvironment());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.10");
        request.addHeader("X-Forwarded-For", "203.0.113.10");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.10");
    }
}
