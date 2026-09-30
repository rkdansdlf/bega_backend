package com.example.auth.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.common.config.AllowedOriginResolver;
import com.example.common.exception.ForbiddenBusinessException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class CookieMutationOriginValidatorTest {

    private final AllowedOriginResolver allowedOriginResolver = mock(AllowedOriginResolver.class);
    private final CookieMutationOriginValidator validator = new CookieMutationOriginValidator(allowedOriginResolver);

    @Test
    void acceptsAllowedOrigin() {
        when(allowedOriginResolver.resolve()).thenReturn(List.of("https://www.begabaseball.xyz"));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/logout");
        request.addHeader("Origin", "https://www.begabaseball.xyz");

        assertThatCode(() -> validator.validate(request, "INVALID_LOGOUT_ORIGIN"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingOriginAndReferer() {
        when(allowedOriginResolver.resolve()).thenReturn(List.of("https://www.begabaseball.xyz"));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/logout");

        assertThatThrownBy(() -> validator.validate(request, "INVALID_LOGOUT_ORIGIN"))
                .isInstanceOf(ForbiddenBusinessException.class);
    }

    @Test
    void rejectsCrossOriginReferer() {
        when(allowedOriginResolver.resolve()).thenReturn(List.of("https://www.begabaseball.xyz"));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/logout");
        request.addHeader("Referer", "https://evil.example/logout");

        assertThatThrownBy(() -> validator.validate(request, "INVALID_LOGOUT_ORIGIN"))
                .isInstanceOf(ForbiddenBusinessException.class);
    }
}
