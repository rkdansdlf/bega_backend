package com.example.auth.util;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class AccessTokenResolverTest {

    @Test
    void resolvesBearerWhenAccessCookieIsAbsent() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("XSRF-TOKEN", "csrf-test"));
        request.addHeader("Authorization", "Bearer header-token");
        assertThat(AccessTokenResolver.resolve(request)).isEqualTo("header-token");
    }

    @Test
    void firstAccessCookieTakesPrecedenceOverHeaderAndLaterCookies() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("Authorization", "first"), new Cookie("Authorization", "second"));
        request.addHeader("Authorization", "Bearer header-token");
        assertThat(AccessTokenResolver.resolve(request)).isEqualTo("first");
    }

    @Test
    void emptyAccessCookieDoesNotSwitchAuthenticatedIdentityToHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("Authorization", ""));
        request.addHeader("Authorization", "Bearer header-token");
        assertThat(AccessTokenResolver.resolve(request)).isEmpty();
    }

    @Test
    void missingTokenAndNonBearerSchemeDoNotAuthenticate() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertThat(AccessTokenResolver.resolve(request)).isNull();
        request.addHeader("Authorization", "Basic test-value");
        assertThat(AccessTokenResolver.resolve(request)).isNull();
    }
}
