package com.example.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

public final class RedisIntegrationTestSupport {

    private static final String DEFAULT_HOST = "127.0.0.1";
    private static final int DEFAULT_PORT = 16379;
    private static final String DEFAULT_CHANNEL = "bega:realtime:v1";

    private RedisIntegrationTestSupport() {
    }

    public static LettuceConnectionFactory connectionFactory() {
        String username = System.getProperty("realtime.redis.username", "").trim();
        String passwordFile = System.getProperty("realtime.redis.password-file", "").trim();
        if (username.isEmpty() || passwordFile.isEmpty()) {
            throw new IllegalStateException(
                    "realtime.redis.username and realtime.redis.password-file are required");
        }

        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(host(), port());
        configuration.setUsername(username);
        configuration.setPassword(readPassword(passwordFile));

        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(configuration);
        connectionFactory.afterPropertiesSet();
        return connectionFactory;
    }

    public static String channel() {
        return System.getProperty("realtime.redis.channel", DEFAULT_CHANNEL);
    }

    private static String host() {
        return System.getProperty("realtime.redis.host", DEFAULT_HOST);
    }

    private static int port() {
        return Integer.parseInt(System.getProperty(
                "realtime.redis.port",
                Integer.toString(DEFAULT_PORT)));
    }

    private static RedisPassword readPassword(String passwordFile) {
        byte[] passwordBytes;
        try {
            passwordBytes = Files.readAllBytes(Path.of(passwordFile));
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read the Redis integration password file", exception);
        }

        try {
            if (passwordBytes.length == 0) {
                throw new IllegalStateException("Redis integration password file must not be empty");
            }
            for (byte value : passwordBytes) {
                int unsigned = Byte.toUnsignedInt(value);
                if (unsigned < 0x21 || unsigned > 0x7e) {
                    throw new IllegalStateException(
                            "Redis integration password must be printable ASCII without whitespace");
                }
            }
            return RedisPassword.of(new String(passwordBytes, java.nio.charset.StandardCharsets.US_ASCII));
        } finally {
            Arrays.fill(passwordBytes, (byte) 0);
        }
    }
}
