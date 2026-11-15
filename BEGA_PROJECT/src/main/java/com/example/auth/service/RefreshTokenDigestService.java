package com.example.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class RefreshTokenDigestService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final byte[] pepper;

    public RefreshTokenDigestService(
            @Value("${app.auth.refresh-token-pepper:local-dev-refresh-token-pepper}") String pepper) {
        if (!StringUtils.hasText(pepper)) {
            throw new IllegalArgumentException("refresh token pepper must not be blank");
        }
        this.pepper = pepper.getBytes(StandardCharsets.UTF_8);
    }

    public String digest(String rawToken) {
        if (!StringUtils.hasText(rawToken)) {
            throw new IllegalArgumentException("refresh token must not be blank");
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(pepper, HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", exception);
        }
    }
}
