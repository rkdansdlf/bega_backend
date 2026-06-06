package com.example.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.catalina.connector.Connector;
import org.apache.coyote.http11.AbstractHttp11Protocol;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class TomcatUploadTimeoutConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TomcatUploadTimeoutConfig.class);

    @Test
    void customizeConnectorEnablesUploadTimeoutWithConfiguredValue() {
        TomcatUploadTimeoutConfig config = new TomcatUploadTimeoutConfig(45_000L, true);
        Connector connector = connector();

        config.customizeConnector(connector);

        AbstractHttp11Protocol<?> protocol = (AbstractHttp11Protocol<?>) connector.getProtocolHandler();
        assertThat(protocol.getConnectionUploadTimeout()).isEqualTo(45_000);
        assertThat(protocol.getDisableUploadTimeout()).isFalse();
    }

    @Test
    void customizeConnectorCanDisableSeparateUploadTimeout() {
        TomcatUploadTimeoutConfig config = new TomcatUploadTimeoutConfig(45_000L, false);
        Connector connector = connector();

        config.customizeConnector(connector);

        AbstractHttp11Protocol<?> protocol = (AbstractHttp11Protocol<?>) connector.getProtocolHandler();
        assertThat(protocol.getConnectionUploadTimeout()).isEqualTo(45_000);
        assertThat(protocol.getDisableUploadTimeout()).isTrue();
    }

    @Test
    void timeoutIsClampedToSafeMinimum() {
        TomcatUploadTimeoutConfig config = new TomcatUploadTimeoutConfig(0L, true);
        Connector connector = connector();

        config.customizeConnector(connector);

        AbstractHttp11Protocol<?> protocol = (AbstractHttp11Protocol<?>) connector.getProtocolHandler();
        assertThat(protocol.getConnectionUploadTimeout()).isEqualTo(1_000);
    }

    @Test
    void propertiesBindToTomcatUploadTimeoutConfig() {
        contextRunner
                .withPropertyValues(
                        "app.tomcat.connection-upload-timeout-ms=15000",
                        "app.tomcat.upload-timeout-enabled=false")
                .run(context -> {
                    TomcatUploadTimeoutConfig config = context.getBean(TomcatUploadTimeoutConfig.class);
                    Connector connector = connector();

                    config.customizeConnector(connector);

                    AbstractHttp11Protocol<?> protocol = (AbstractHttp11Protocol<?>) connector.getProtocolHandler();
                    assertThat(protocol.getConnectionUploadTimeout()).isEqualTo(15_000);
                    assertThat(protocol.getDisableUploadTimeout()).isTrue();
                });
    }

    private Connector connector() {
        return new Connector("org.apache.coyote.http11.Http11NioProtocol");
    }
}
