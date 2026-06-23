package com.example.common.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.Connector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class TomcatUploadTimeoutConfig {

    private static final int MIN_UPLOAD_TIMEOUT_MS = 1_000;

    private final int connectionUploadTimeoutMs;
    private final boolean uploadTimeoutEnabled;

    public TomcatUploadTimeoutConfig(
            @Value("${app.tomcat.connection-upload-timeout-ms:60000}") long connectionUploadTimeoutMs,
            @Value("${app.tomcat.upload-timeout-enabled:true}") boolean uploadTimeoutEnabled) {
        this.connectionUploadTimeoutMs = normalizeTimeoutMs(connectionUploadTimeoutMs);
        this.uploadTimeoutEnabled = uploadTimeoutEnabled;
    }

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> tomcatUploadTimeoutCustomizer() {
        return factory -> factory.addConnectorCustomizers(this::customizeConnector);
    }

    void customizeConnector(Connector connector) {
        connector.setProperty("connectionUploadTimeout", Integer.toString(connectionUploadTimeoutMs));
        connector.setProperty("disableUploadTimeout", Boolean.toString(!uploadTimeoutEnabled));
        log.info(
                "Configured Tomcat upload timeout enabled={} connectionUploadTimeoutMs={}",
                uploadTimeoutEnabled,
                connectionUploadTimeoutMs);
    }

    private int normalizeTimeoutMs(long timeoutMs) {
        long normalized = Math.max(MIN_UPLOAD_TIMEOUT_MS, timeoutMs);
        return (int) Math.min(Integer.MAX_VALUE, normalized);
    }
}
