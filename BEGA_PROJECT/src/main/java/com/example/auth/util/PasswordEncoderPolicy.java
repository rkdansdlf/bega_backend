package com.example.auth.util;

/** Shared password hash settings for configured and timing-equalization encoders. */
public final class PasswordEncoderPolicy {

    // V138 migrated existing BCrypt hashes at cost 10; configured BCrypt encoding uses cost 12.
    public static final int MIGRATED_BCRYPT_STRENGTH = 10;
    public static final int DEFAULT_BCRYPT_STRENGTH = 12;

    private PasswordEncoderPolicy() {
    }
}
