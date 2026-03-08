package com.example.auth.config;

import com.example.ai.config.AiServiceSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    @Test
    @DisplayName("AI proxy should remain authenticated by default even in dev profile")
    void allowUnauthenticatedAiProxy_defaultsToFalse() throws Exception {
        SecurityConfig securityConfig = newSecurityConfig("dev");

        setPrivateField(securityConfig, "publicAiProxyInDevEnabled", false);

        assertThat(securityConfig.allowUnauthenticatedAiProxy()).isFalse();
    }

    @Test
    @DisplayName("AI proxy public mode should require explicit opt-in in dev/local profile")
    void allowUnauthenticatedAiProxy_requiresExplicitOptIn() throws Exception {
        SecurityConfig securityConfig = newSecurityConfig("local");

        setPrivateField(securityConfig, "publicAiProxyInDevEnabled", true);

        assertThat(securityConfig.allowUnauthenticatedAiProxy()).isTrue();
    }

    @Test
    @DisplayName("AI proxy public mode should stay disabled outside dev/local even when flag is set")
    void allowUnauthenticatedAiProxy_staysDisabledOutsideDevLocal() throws Exception {
        SecurityConfig securityConfig = newSecurityConfig("prod");

        setPrivateField(securityConfig, "publicAiProxyInDevEnabled", true);

        assertThat(securityConfig.allowUnauthenticatedAiProxy()).isFalse();
    }

    @Test
    @DisplayName("Anonymous authentication should not satisfy AI proxy auth guard")
    void isAuthenticatedPrincipal_rejectsAnonymousAuthentication() {
        SecurityConfig securityConfig = newSecurityConfig("dev");
        AnonymousAuthenticationToken anonymousAuthentication = new AnonymousAuthenticationToken(
                "test-key",
                "anonymousUser",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

        assertThat(securityConfig.isAuthenticatedPrincipal(anonymousAuthentication, new Object())).isFalse();
    }

    @Test
    @DisplayName("Authenticated user should satisfy AI proxy auth guard")
    void isAuthenticatedPrincipal_acceptsAuthenticatedUser() {
        SecurityConfig securityConfig = newSecurityConfig("dev");
        UsernamePasswordAuthenticationToken authenticatedUser =
                UsernamePasswordAuthenticationToken.authenticated(
                        "user",
                        "n/a",
                        AuthorityUtils.createAuthorityList("ROLE_USER"));

        assertThat(securityConfig.isAuthenticatedPrincipal(authenticatedUser, new Object())).isTrue();
    }

    @Test
    @DisplayName("Matching internal AI token should satisfy AI proxy guard in dev")
    void hasValidAiProxyInternalToken_acceptsMatchingTokenInDev() {
        AiServiceSettings aiServiceSettings = mock(AiServiceSettings.class);
        when(aiServiceSettings.getResolvedInternalToken()).thenReturn("expected-token");
        SecurityConfig securityConfig = newSecurityConfig(aiServiceSettings, "dev");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Internal-Api-Key", "expected-token");

        assertThat(securityConfig.hasValidAiProxyInternalToken(new RequestAuthorizationContext(request))).isTrue();
    }

    @Test
    @DisplayName("Internal AI token should remain disabled outside dev/local")
    void hasValidAiProxyInternalToken_rejectsProdProfile() {
        AiServiceSettings aiServiceSettings = mock(AiServiceSettings.class);
        when(aiServiceSettings.getResolvedInternalToken()).thenReturn("expected-token");
        SecurityConfig securityConfig = newSecurityConfig(aiServiceSettings, "prod");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Internal-Api-Key", "expected-token");

        assertThat(securityConfig.hasValidAiProxyInternalToken(new RequestAuthorizationContext(request))).isFalse();
    }

    private String[] getPrivateStaticStringArray(String fieldName) throws Exception {
        Field field = SecurityConfig.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (String[]) field.get(null);
    }

    private void setPrivateField(Object target, String fieldName, Object value) throws Exception {
        Field field = SecurityConfig.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private SecurityConfig newSecurityConfig(String... activeProfiles) {
        return newSecurityConfig(mock(AiServiceSettings.class), activeProfiles);
    }

    private SecurityConfig newSecurityConfig(AiServiceSettings aiServiceSettings, String... activeProfiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(activeProfiles);

        return new SecurityConfig(
                mock(com.example.auth.oauth2.CustomOAuth2UserService.class),
                mock(com.example.auth.oauth2.CustomSuccessHandler.class),
                mock(com.example.auth.util.JWTUtil.class),
                mock(com.example.auth.oauth2.CookieAuthorizationRequestRepository.class),
                mock(com.example.auth.service.TokenBlacklistService.class),
                mock(com.example.auth.repository.UserRepository.class),
                mock(com.example.auth.service.AuthSecurityMonitoringService.class),
                environment,
                aiServiceSettings);
    }
}
