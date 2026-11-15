package com.example.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;

class FlywayProductionPolicyTest {

    @Test
    @DisplayName("운영 Flyway는 이력 검증과 destructive/out-of-order 차단을 강제한다")
    void productionFlywayPolicyIsFailClosed() throws Exception {
        List<PropertySource<?>> propertySources = new YamlPropertySourceLoader().load(
                "application",
                new FileSystemResource("src/main/resources/application.yml"));
        PropertySource<?> production = propertySources.stream()
                .filter(source -> "prod".equals(source.getProperty("spring.config.activate.on-profile")))
                .findFirst()
                .orElseThrow();

        assertThat(production.getProperty("spring.flyway.validate-on-migrate")).isEqualTo(true);
        assertThat(production.getProperty("spring.flyway.clean-disabled")).isEqualTo(true);
        assertThat(production.getProperty("spring.flyway.out-of-order")).isEqualTo(false);
        assertThat(production.getProperty("spring.flyway.ignore-migration-patterns[0]")).isNull();
        assertThat(production.getProperty("app.flyway.auto-repair")).isEqualTo(false);
    }
}
