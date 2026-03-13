package com.example.auth.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FrontendRedirectUtilTest {

    @Test
    void sanitizeRedirect_acceptsSafeRelativePath() {
        assertThat(FrontendRedirectUtil.sanitizeRedirect("/mypage?view=accountSettings#security"))
                .isEqualTo("/mypage?view=accountSettings#security");
    }

    @Test
    void sanitizeRedirect_rejectsExternalOrDisallowedPath() {
        assertThat(FrontendRedirectUtil.sanitizeRedirect("https://evil.example")).isNull();
        assertThat(FrontendRedirectUtil.sanitizeRedirect("/login")).isNull();
        assertThat(FrontendRedirectUtil.sanitizeRedirect("//evil.example")).isNull();
    }
}
