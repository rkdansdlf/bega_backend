package com.example.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;

import org.junit.jupiter.api.Test;

class EmailJobTokenCipherTest {

    private static final String TEST_KEY = Base64.getEncoder().encodeToString(new byte[32]);

    private final EmailJobTokenCipher cipher = new EmailJobTokenCipher(TEST_KEY);

    @Test
    void encryptedTokenRoundTripsWithoutStoringPlaintext() {
        String rawToken = "synthetic-password-reset-token";

        String envelope = cipher.encrypt(EmailJobTokenCipher.Purpose.PASSWORD_RESET, rawToken);

        assertThat(envelope)
                .startsWith(EmailJobTokenCipher.ENVELOPE_PREFIX)
                .doesNotContain(rawToken);
        assertThat(cipher.decrypt(EmailJobTokenCipher.Purpose.PASSWORD_RESET, envelope))
                .isEqualTo(rawToken);
    }

    @Test
    void eachEncryptionUsesAFreshNonce() {
        String first = cipher.encrypt(EmailJobTokenCipher.Purpose.PASSWORD_RESET, "same-token");
        String second = cipher.encrypt(EmailJobTokenCipher.Purpose.PASSWORD_RESET, "same-token");

        assertThat(second).isNotEqualTo(first);
    }

    @Test
    void tamperedEnvelopeIsRejected() {
        String envelope = cipher.encrypt(EmailJobTokenCipher.Purpose.PASSWORD_RESET, "synthetic-token");
        String payload = envelope.substring(EmailJobTokenCipher.ENVELOPE_PREFIX.length());
        char replacement = payload.charAt(0) == 'A' ? 'B' : 'A';
        String tamperedEnvelope = EmailJobTokenCipher.ENVELOPE_PREFIX
                + replacement
                + payload.substring(1);

        assertThatThrownBy(() -> cipher.decrypt(
                EmailJobTokenCipher.Purpose.PASSWORD_RESET,
                tamperedEnvelope))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void envelopeCannotBeReusedForAnotherEmailPurpose() {
        String envelope = cipher.encrypt(EmailJobTokenCipher.Purpose.PASSWORD_RESET, "synthetic-token");

        assertThatThrownBy(() -> cipher.decrypt(
                EmailJobTokenCipher.Purpose.ACCOUNT_DELETION_RECOVERY,
                envelope))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void legacyPlaintextJobArgumentsRemainReadable() {
        assertThat(cipher.decrypt(
                EmailJobTokenCipher.Purpose.ACCOUNT_DELETION_RECOVERY,
                "legacy-queued-token"))
                .isEqualTo("legacy-queued-token");
    }

    @Test
    void unsupportedEnvelopeVersionIsRejected() {
        assertThatThrownBy(() -> cipher.decrypt(
                EmailJobTokenCipher.Purpose.PASSWORD_RESET,
                "bega-job-token:v2:payload"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void encryptionRequiresAConfiguredKey() {
        EmailJobTokenCipher unconfiguredCipher = new EmailJobTokenCipher("");

        assertThatThrownBy(() -> unconfiguredCipher.encrypt(
                EmailJobTokenCipher.Purpose.PASSWORD_RESET,
                "synthetic-token"))
                .isInstanceOf(IllegalStateException.class);
    }
}
