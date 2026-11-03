package com.example.common.config;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Local Oracle RAG Flyway CLI tests")
class RagFlywayLocalCliTest {

    private Map<String, String> validEnvironment() {
        Map<String, String> environment = new HashMap<>();
        environment.put("APP_ENV", "local");
        environment.put("BEGA_DISABLE_DOTENV", "true");
        environment.put("RAG_FLYWAY_ENABLED", "true");
        environment.put("RAG_FLYWAY_URL", "jdbc:oracle:thin:@//127.0.0.1:1521/LOCALPDB1");
        environment.put("RAG_FLYWAY_USERNAME", "LOCAL_RAG_E2E");
        environment.put("RAG_FLYWAY_PASSWORD", "local-password");
        environment.put("RAG_FLYWAY_LOCATIONS", RagFlywayMigrationConfig.DEFAULT_LOCATION);
        environment.put("RAG_FLYWAY_HISTORY_TABLE", RagFlywayMigrationConfig.HISTORY_TABLE);
        return environment;
    }

    @Test
    @DisplayName("local CLI accepts only the explicit loopback migration contract")
    void acceptsExplicitLoopbackContract() {
        RagFlywayLocalCli.Settings settings = RagFlywayLocalCli.settings(validEnvironment());

        assertThat(settings.url()).contains("127.0.0.1");
        assertThat(settings.username()).isEqualTo("LOCAL_RAG_E2E");
        assertThat(settings.historyTable()).isEqualTo(RagFlywayMigrationConfig.HISTORY_TABLE);
    }

    @Test
    @DisplayName("local CLI rejects remote Oracle endpoints")
    void rejectsRemoteEndpoint() {
        Map<String, String> environment = validEnvironment();
        environment.put("RAG_FLYWAY_URL", "jdbc:oracle:thin:@//prod-db.example:1521/PRODPDB");

        assertThatThrownBy(() -> RagFlywayLocalCli.settings(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("loopback");
    }

    @Test
    @DisplayName("local CLI requires dotenv loading to be disabled")
    void rejectsDotenvFallback() {
        Map<String, String> environment = validEnvironment();
        environment.remove("BEGA_DISABLE_DOTENV");

        assertThatThrownBy(() -> RagFlywayLocalCli.settings(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BEGA_DISABLE_DOTENV");
    }
}
