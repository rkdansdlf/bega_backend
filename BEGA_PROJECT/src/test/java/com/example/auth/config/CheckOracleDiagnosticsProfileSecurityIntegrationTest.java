package com.example.auth.config;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.jwt.secret=test-jwt-secret-64-characters-long-for-hs512-signature-tests-key-1234567890",
        "spring.jwt.refresh-expiration=86400000",
        "app.oauth2.cookie-secret=test-oauth2-cookie-secret-for-integration-tests-1234567890",
        "spring.datasource.url=jdbc:h2:mem:check_oracle_diagnostics_primary;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "baseball.datasource.url=jdbc:h2:mem:check_oracle_diagnostics_baseball;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "baseball.datasource.driver-class-name=org.h2.Driver",
        "baseball.datasource.username=sa",
        "baseball.datasource.password=",
        "baseball.flyway.enabled=false",
        "baseball.jpa.hibernate.ddl-auto=none",
        "spring.jpa.open-in-view=false",
        "jobrunr.background-job-server.enabled=false",
        "jobrunr.dashboard.enabled=false",
        "app.dev-data.enabled=false",
        "app.home.bootstrap-warmup.enabled=false",
        "app.prediction.warmup.enabled=false",
        "app.leaderboard.game-result-scheduler.enabled=false",
        "app.cheer.post-sync-scheduler.enabled=false",
        "app.media.cleanup.enabled=false",
        "app.realtime.outbox.enabled=false",
        "ai.ingest.enabled=false",
        "storage.type=oci",
        "oci.s3.endpoint=http://localhost:4566",
        "oci.s3.access-key=test-access-key",
        "oci.s3.secret-key=test-secret-key",
        "oci.s3.bucket=test-bucket",
        "oci.s3.region=ap-seoul-1",
        "app.allowed-origins=https://www.begabaseball.xyz"
})
abstract class AbstractCheckOracleDiagnosticsProfileSecurityIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;
}

@ActiveProfiles({"dev", "test"})
class CheckOracleDiagnosticsDevProfileSecurityIntegrationTest
        extends AbstractCheckOracleDiagnosticsProfileSecurityIntegrationTest {

    @Test
    void anonymousRequestRequiresAuthenticationInDevProfile() throws Exception {
        mockMvc.perform(get("/api/test/counts"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/test/games-range")
                        .param("start", "2026-01-01")
                        .param("end", "2026-01-02"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedRequestCanReachDiagnosticsInDevProfile() throws Exception {
        mockMvc.perform(get("/api/test/counts")
                        .with(user("diagnostics-test-user").roles("USER")))
                .andExpect(status().isOk());
    }
}

@ActiveProfiles("local")
class CheckOracleDiagnosticsLocalProfileSecurityIntegrationTest
        extends AbstractCheckOracleDiagnosticsProfileSecurityIntegrationTest {

    @Test
    void anonymousRequestCanReachDiagnosticsOnlyInExactLocalProfile() throws Exception {
        mockMvc.perform(get("/api/test/counts"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/test/games-range")
                        .param("start", "2026-01-01")
                        .param("end", "2026-01-02"))
                .andExpect(status().isOk());
    }
}

@ActiveProfiles({"dev", "local"})
class CheckOracleDiagnosticsMixedProfileSecurityIntegrationTest
        extends AbstractCheckOracleDiagnosticsProfileSecurityIntegrationTest {

    @Test
    void anonymousRequestRequiresAuthenticationWhenLocalIsCombinedWithDev() throws Exception {
        mockMvc.perform(get("/api/test/counts"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/test/games-range")
                        .param("start", "2026-01-01")
                        .param("end", "2026-01-02"))
                .andExpect(status().isUnauthorized());
    }
}
