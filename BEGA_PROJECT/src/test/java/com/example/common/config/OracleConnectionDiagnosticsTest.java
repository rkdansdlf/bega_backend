package com.example.common.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OracleConnectionDiagnosticsTest {

    @Test
    void detectsOra12506InCauseChain() {
        RuntimeException throwable = new RuntimeException(
                "top",
                new IllegalStateException("Failed to initialize pool: ORA-12506: listener rejected connection")
        );

        assertThat(OracleConnectionDiagnostics.isListenerAclFailure(throwable)).isTrue();
    }

    @Test
    void buildsActionableListenerAclMessage() {
        String message = OracleConnectionDiagnostics.buildListenerAclFailureMessage(
                "jdbc:oracle:thin:@efh9m9c9h109963k_high?TNS_ADMIN=/app/wallet",
                "ADMIN",
                "/app/wallet"
        );

        assertThat(message).contains("ORA-12506");
        assertThat(message).contains("efh9m9c9h109963k_high");
        assertThat(message).contains("ADMIN");
        assertThat(message).contains("/app/wallet");
    }

    @Test
    void extractsTargetAliasWithoutQueryString() {
        assertThat(OracleConnectionDiagnostics.extractOracleTarget(
                "jdbc:oracle:thin:@efh9m9c9h109963k_high?TNS_ADMIN=/app/wallet"
        )).isEqualTo("efh9m9c9h109963k_high");
    }
}
