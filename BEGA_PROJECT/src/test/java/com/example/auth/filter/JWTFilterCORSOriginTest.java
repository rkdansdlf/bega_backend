package com.example.auth.filter;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;

public class JWTFilterCORSOriginTest {

    @Test
    public void testMatchesOriginPatternWithWildcard() {
        // Create an instance of the class containing the private method
        JWTFilter jwtFilter = new JWTFilter(null, true, List.of("https://*.frontend-dfl.pages.dev"), null, null, null);

        // Define origin strings
        String pattern = "https://*.frontend-dfl.pages.dev";
        String validOrigin = "https://pr-123.frontend-dfl.pages.dev";
        String validOrigin2 = "https://test.frontend-dfl.pages.dev";
        String invalidOrigin = "https://pr-123.anothertest.pages.dev";

        // Invoke private matchesOriginPattern method via Reflection
        Boolean result1 = ReflectionTestUtils.invokeMethod(jwtFilter, "matchesOriginPattern", validOrigin, pattern);
        Boolean result2 = ReflectionTestUtils.invokeMethod(jwtFilter, "matchesOriginPattern", validOrigin2, pattern);
        Boolean result3 = ReflectionTestUtils.invokeMethod(jwtFilter, "matchesOriginPattern", invalidOrigin, pattern);

        assertThat(result1).isTrue();
        assertThat(result2).isTrue();
        assertThat(result3).isFalse();
    }
}
