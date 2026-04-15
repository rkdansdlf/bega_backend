package com.example.auth.config;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.profiles.active=test",
        "spring.jwt.secret=test-jwt-secret-32-characters-long",
        "spring.jwt.refresh-expiration=86400000",
        "spring.datasource.url=jdbc:h2:mem:security_surface_hardening;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.jpa.open-in-view=false",
        "jobrunr.background-job-server.enabled=false",
        "jobrunr.dashboard.enabled=false",
        "storage.type=oci",
        "oci.s3.endpoint=http://localhost:4566",
        "oci.s3.access-key=test-access-key",
        "oci.s3.secret-key=test-secret-key",
        "oci.s3.bucket=test-bucket",
        "oci.s3.region=ap-seoul-1",
        "app.allowed-origins=https://www.begabaseball.xyz"
})
@DisplayName("Security surface hardening integration tests")
class SecuritySurfaceHardeningIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("mypage preflight should allow configured production frontend origin")
    void mypagePreflight_allowsConfiguredProductionFrontendOrigin() throws Exception {
        mockMvc.perform(options("/api/auth/mypage")
                        .header(HttpHeaders.ORIGIN, "https://www.begabaseball.xyz")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://www.begabaseball.xyz"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, Matchers.containsString("GET")));
    }

    @Test
    @DisplayName("unauthenticated mypage should still include CORS headers for configured production frontend origin")
    void mypageUnauthorized_includesCorsHeadersForConfiguredProductionFrontendOrigin() throws Exception {
        mockMvc.perform(get("/api/auth/mypage")
                        .header(HttpHeaders.ORIGIN, "https://www.begabaseball.xyz"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://www.begabaseball.xyz"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    @DisplayName("check-email should no longer be publicly accessible")
    void checkEmail_requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/auth/check-email").param("email", "test@example.com"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("email-to-id should no longer be publicly accessible")
    void emailToId_requiresAuthenticationAndHasNoHandler() throws Exception {
        mockMvc.perform(get("/api/users/email-to-id").param("email", "test@example.com"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/users/email-to-id")
                        .param("email", "test@example.com")
                        .with(user("tester").roles("USER")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("websocket handshake endpoint should reject unauthenticated access")
    void websocketEndpoint_rejectsUnauthenticatedAccess() throws Exception {
        mockMvc.perform(get("/ws")
                        .header("Connection", "Upgrade")
                        .header("Upgrade", "websocket")
                        .header("Sec-WebSocket-Version", "13")
                        .header("Sec-WebSocket-Key", "dGhlIHNhbXBsZSBub25jZQ=="))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("checkin endpoints should reject unauthenticated reads")
    void checkinEndpoints_rejectUnauthenticatedReads() throws Exception {
        mockMvc.perform(get("/api/checkin/party/1"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/checkin/user/1"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/checkin/party/1/count"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("social-verified should reject unauthenticated access")
    void socialVerified_rejectsUnauthenticatedAccess() throws Exception {
        mockMvc.perform(get("/api/users/1/social-verified"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("numeric block action route should no longer have a handler")
    void numericBlockActionRoute_isRemoved() throws Exception {
        mockMvc.perform(post("/api/users/1/block")
                        .with(user("tester").roles("USER")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("numeric public profile route should no longer be publicly accessible")
    void numericPublicProfileRoute_requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/users/1/profile"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/users/1/profile")
                        .with(user("tester").roles("USER")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("handle-based public profile route should remain public")
    void handleBasedPublicProfileRoute_remainsPublic() throws Exception {
        mockMvc.perform(get("/api/users/profile/missing"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("numeric follow read routes should no longer be publicly accessible")
    void numericFollowReadRoutes_requireAuthentication() throws Exception {
        mockMvc.perform(get("/api/users/1/follow-counts"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/users/1/followers"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/users/1/following"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/users/1/follow-counts")
                        .with(user("tester").roles("USER")))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/users/1/followers")
                        .with(user("tester").roles("USER")))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/users/1/following")
                        .with(user("tester").roles("USER")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("handle-based follow read routes should remain public")
    void handleBasedFollowReadRoutes_remainPublic() throws Exception {
        mockMvc.perform(get("/api/users/profile/missing/follow-counts"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/users/profile/missing/followers"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/users/profile/missing/following"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("numeric follow action routes should no longer have handlers")
    void numericFollowActionRoutes_areRemoved() throws Exception {
        mockMvc.perform(post("/api/users/1/follow")
                        .with(user("tester").roles("USER")))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/users/1/follow/notify")
                        .param("notify", "true")
                        .with(user("tester").roles("USER")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("numeric host party route should no longer be publicly accessible")
    void numericHostPartyRoute_requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/parties/host/1"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/parties/host/1")
                        .with(user("tester").roles("USER")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("handle-based host party route should remain public")
    void handleBasedHostPartyRoute_remainsPublic() throws Exception {
        mockMvc.perform(get("/api/parties/profile/missing"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("legacy notification unread-count route should no longer be exposed")
    void legacyNotificationUnreadCountRoute_isRemoved() throws Exception {
        mockMvc.perform(get("/api/notifications/user/1/unread-count"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/notifications/user/1/unread-count")
                        .with(user("tester").roles("USER")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("ranking share route should reject legacy numeric user identifiers")
    void rankingShareRoute_rejectsLegacyNumericUserIdentifiers() throws Exception {
        mockMvc.perform(get("/api/predictions/ranking/share/1/2026"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("numeric leaderboard user routes should no longer be publicly accessible")
    void numericLeaderboardUserRoutes_requireAuthentication() throws Exception {
        mockMvc.perform(get("/api/leaderboard/user/1"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/leaderboard/users/1/rank"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/leaderboard/user/1")
                        .with(user("tester").roles("USER")))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/leaderboard/users/1/rank")
                        .with(user("tester").roles("USER")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("handle-based leaderboard user routes should remain public")
    void handleBasedLeaderboardUserRoutes_remainPublic() throws Exception {
        mockMvc.perform(get("/api/leaderboard/profile/missing"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/leaderboard/profile/missing/rank"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("numeric review average route should no longer be publicly accessible")
    void numericReviewAverageRoute_requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/reviews/user/1/average"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/reviews/user/1/average")
                        .with(user("tester").roles("USER")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("party review list should no longer be publicly accessible")
    void partyReviewList_requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/reviews/party/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("handle-based review average route should no longer be exposed")
    void handleBasedReviewAverageRoute_requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/reviews/profile/missing/average"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/reviews/profile/missing/average")
                        .with(user("tester").roles("USER")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("dashboard routes should be admin-only")
    void dashboardRoutes_areAdminOnly() throws Exception {
        mockMvc.perform(get("/dashboard/probe"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/dashboard/probe")
                        .with(user("tester").roles("USER")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/dashboard/probe")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string("dashboard-ok"));
    }

    @TestConfiguration
    static class DashboardProbeConfig {

        @Bean
        DashboardProbeController dashboardProbeController() {
            return new DashboardProbeController();
        }
    }

    @RestController
    static class DashboardProbeController {

        @GetMapping("/dashboard/probe")
        String probe() {
            return "dashboard-ok";
        }
    }
}
