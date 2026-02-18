package com.example.auth.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SecurityConfig tests")
class SecurityConfigTest {

    @Test
    @DisplayName("PUBLIC_PARTY_GET_ENDPOINTS should expose public party read routes")
    void publicPartyGetEndpointsContainsPartiesRoutes() throws Exception {
        String[] publicPartyGetEndpoints = getPrivateStaticStringArray("PUBLIC_PARTY_GET_ENDPOINTS");

        assertThat(publicPartyGetEndpoints).contains(
                "/api/parties",
                "/api/parties/search",
                "/api/parties/status/*",
                "/api/parties/host/*",
                "/api/parties/upcoming");
    }

    @Test
    @DisplayName("PUBLIC_* constants should not expose /api/parties/my")
    void publicGetEndpoints_doesNotContainMyParties() throws Exception {
        String[] publicGetEndpoints = getPrivateStaticStringArray("PUBLIC_GET_ENDPOINTS");
        String[] publicPartyGetEndpoints = getPrivateStaticStringArray("PUBLIC_PARTY_GET_ENDPOINTS");

        assertThat(publicGetEndpoints).doesNotContain("/api/parties/my");
        assertThat(publicPartyGetEndpoints).doesNotContain("/api/parties/my");
    }

    private String[] getPrivateStaticStringArray(String fieldName) throws Exception {
        Field field = SecurityConfig.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (String[]) field.get(null);
    }
}
