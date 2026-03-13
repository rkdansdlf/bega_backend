package com.example.mate.service.payout;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEHeader;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.RSAEncrypter;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Component
public class TossPayoutEncryptionService {

    private final ObjectMapper objectMapper;

    public TossPayoutEncryptionService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String encryptPayload(Object payload, String publicKeySource, String publicKeyPath) {
        if ((publicKeySource == null || publicKeySource.isBlank())
                && (publicKeyPath == null || publicKeyPath.isBlank())) {
            throw new IllegalStateException("TOSS payout ENCRYPTION 모드에는 공개키 설정이 필요합니다.");
        }

        String jsonPayload = toJson(payload);
        RSAPublicKey publicKey = loadPublicKey(publicKeySource, publicKeyPath);

        JWEHeader header = new JWEHeader.Builder(JWEAlgorithm.RSA_OAEP_256, EncryptionMethod.A128GCM)
                .contentType("application/json")
                .build();
        JWEObject jweObject = new JWEObject(header, new Payload(jsonPayload));

        try {
            jweObject.encrypt(new RSAEncrypter(publicKey));
            return jweObject.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("TOSS payout JWE 암호화에 실패했습니다.", e);
        }
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("지급 요청 payload 직렬화에 실패했습니다.", e);
        }
    }

    private RSAPublicKey loadPublicKey(String publicKeySource, String publicKeyPath) {
        String keyMaterial = publicKeySource;
        if ((keyMaterial == null || keyMaterial.isBlank()) && publicKeyPath != null && !publicKeyPath.isBlank()) {
            try {
                keyMaterial = Files.readString(Path.of(publicKeyPath), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException("TOSS payout 공개키 파일을 읽을 수 없습니다.", e);
            }
        }

        if (keyMaterial == null || keyMaterial.isBlank()) {
            throw new IllegalStateException("TOSS payout 공개키 값이 비어 있습니다.");
        }

        String normalized = keyMaterial
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");
        try {
            byte[] decoded = Base64.getDecoder().decode(normalized);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return (RSAPublicKey) keyFactory.generatePublic(new X509EncodedKeySpec(decoded));
        } catch (Exception e) {
            throw new IllegalStateException("TOSS payout 공개키 파싱에 실패했습니다.", e);
        }
    }
}

