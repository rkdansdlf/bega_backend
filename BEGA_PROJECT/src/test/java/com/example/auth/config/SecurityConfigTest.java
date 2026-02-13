package com.example.auth.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SecurityConfig tests")
class SecurityConfigTest {

    @Test
    @DisplayName("PUBLIC_GET_ENDPOINTS should keep /api/parties/** public")
    void publicGetEndpoints_containsPartiesWildcard() throws Exception {
        String[] publicGetEndpoints = getPrivateStaticStringArray("PUBLIC_GET_ENDPOINTS");

        assertThat(publicGetEndpoints).contains("/api/parties/**");
    }

    @Test
    @DisplayName("PUBLIC_GET_ENDPOINTS should not expose /api/parties/my")
    void publicGetEndpoints_doesNotContainMyParties() throws Exception {
        String[] publicGetEndpoints = getPrivateStaticStringArray("PUBLIC_GET_ENDPOINTS");

        assertThat(publicGetEndpoints).doesNotContain("/api/parties/my");
    }

    private String[] getPrivateStaticStringArray(String fieldName) throws Exception {
        Field field = SecurityConfig.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (String[]) field.get(null);
    }
}

