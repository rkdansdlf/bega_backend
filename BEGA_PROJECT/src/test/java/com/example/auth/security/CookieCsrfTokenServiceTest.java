package com.example.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.common.exception.ForbiddenBusinessException;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class CookieCsrfTokenServiceTest {

    private final CookieCsrfTokenService service = new CookieCsrfTokenService();

    @Test
    void validatesMatchingDoubleSubmitToken() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(CookieCsrfTokenService.COOKIE_NAME, "csrf-token"));
        request.addHeader(CookieCsrfTokenService.HEADER_NAME, "csrf-token");

        service.validate(request);
    }

    @Test
    void rejectsMissingOrMismatchedDoubleSubmitToken() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(CookieCsrfTokenService.COOKIE_NAME, "csrf-token"));
        request.addHeader(CookieCsrfTokenService.HEADER_NAME, "different-token");

        assertThatThrownBy(() -> service.validate(request))
                .isInstanceOf(ForbiddenBusinessException.class)
                .hasMessageContaining("CSRF");
    }

    @Test
    void issuesUnpredictableUrlSafeTokens() {
        String first = service.issueToken();
        String second = service.issueToken();

        assertThat(first).hasSizeGreaterThanOrEqualTo(40).doesNotContain("=", "+", "/");
        assertThat(second).isNotEqualTo(first);
    }
}
