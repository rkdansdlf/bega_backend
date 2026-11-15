package com.example.auth.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Protects one-time tokens before they are captured in persistent email-job arguments.
 * APP_MAIL_JOB_TOKEN_ENCRYPTION_KEY must be a stable Base64-encoded 32-byte key.
 */
@Component
public class EmailJobTokenCipher {

    static final String ENVELOPE_PREFIX = "bega-job-token:v1:";
    private static final String ENVELOPE_NAMESPACE = "bega-job-token:";
    private static final String AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int AES_KEY_BYTES = 32;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final String encodedKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public EmailJobTokenCipher(
            @Value("${APP_MAIL_JOB_TOKEN_ENCRYPTION_KEY:}") String encodedKey) {
        this.encodedKey = encodedKey == null ? "" : encodedKey.trim();
    }

    public String encrypt(Purpose purpose, String rawToken) {
        Objects.requireNonNull(purpose, "purpose");
        requireToken(rawToken);
        if (rawToken.startsWith(ENVELOPE_NAMESPACE)) {
            throw new IllegalArgumentException("Token already uses the email job envelope namespace.");
        }

        byte[] nonce = new byte[NONCE_BYTES];
        secureRandom.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION);
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(decodeKey(), "AES"),
                    new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(associatedData(purpose));
            byte[] encrypted = cipher.doFinal(rawToken.getBytes(StandardCharsets.UTF_8));
            byte[] payload = ByteBuffer.allocate(nonce.length + encrypted.length)
                    .put(nonce)
                    .put(encrypted)
                    .array();
            return ENVELOPE_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(payload);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Unable to protect email job token.", e);
        }
    }

    public String decrypt(Purpose purpose, String storedToken) {
        Objects.requireNonNull(purpose, "purpose");
        requireToken(storedToken);
        if (!storedToken.startsWith(ENVELOPE_NAMESPACE)) {
            return storedToken;
        }
        if (!storedToken.startsWith(ENVELOPE_PREFIX)) {
            throw new IllegalArgumentException("Unsupported email job token envelope version.");
        }

        byte[] payload;
        try {
            payload = Base64.getUrlDecoder().decode(storedToken.substring(ENVELOPE_PREFIX.length()));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid email job token envelope.", e);
        }
        if (payload.length < NONCE_BYTES + TAG_BITS / Byte.SIZE) {
            throw new IllegalArgumentException("Invalid email job token envelope.");
        }

        byte[] nonce = Arrays.copyOfRange(payload, 0, NONCE_BYTES);
        byte[] encrypted = Arrays.copyOfRange(payload, NONCE_BYTES, payload.length);
        try {
            Cipher cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION);
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    new SecretKeySpec(decodeKey(), "AES"),
                    new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(associatedData(purpose));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Email job token envelope authentication failed.", e);
        }
    }

    private byte[] decodeKey() {
        if (encodedKey.isEmpty()) {
            throw new IllegalStateException("APP_MAIL_JOB_TOKEN_ENCRYPTION_KEY is required for queued email tokens.");
        }
        try {
            byte[] key = Base64.getDecoder().decode(encodedKey);
            if (key.length != AES_KEY_BYTES) {
                Arrays.fill(key, (byte) 0);
                throw new IllegalStateException("Email job token key must decode to 32 bytes.");
            }
            return key;
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Email job token key must be valid Base64.", e);
        }
    }

    private byte[] associatedData(Purpose purpose) {
        return (ENVELOPE_PREFIX + purpose.wireName).getBytes(StandardCharsets.UTF_8);
    }

    private void requireToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Email job token must not be blank.");
        }
    }

    public enum Purpose {
        PASSWORD_RESET("password-reset"),
        ACCOUNT_DELETION_RECOVERY("account-deletion-recovery");

        private final String wireName;

        Purpose(String wireName) {
            this.wireName = wireName;
        }
    }
}
