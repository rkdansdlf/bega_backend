package com.example.common.clienterror;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ClientErrorRecoveryTokenRedactionTest {
    @Test
    void redactsRecoveryTokensInFragmentsAndStructuredErrors() {
        String input = "failure /account/deletion/recovery#token=fragment-secret "
                + "{\"token\":\"json-secret\\\"suffix\",\"message\":\"safe\"}";
        String result = ClientErrorSupport.sanitizeSensitive(input, 1000);
        assertThat(result).doesNotContain("fragment-secret", "json-secret", "suffix");
        assertThat(result).contains("message", "safe");
    }
}
