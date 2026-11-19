package com.example.stadium.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariConfig;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mock.env.MockEnvironment;

/**
 * 야구 풀(stadiumDataSource)의 Hikari 설정이 어떻게 결정되는지 검증한다.
 *
 * <p>{@link StadiumPostgresJpaConfig#stadiumHikariConfig} 는 코드로 기본값을 넣은 뒤,
 * {@code @ConfigurationProperties("baseball.datasource.hikari")} 바인딩이 그 위에 yml 값을
 * 덮어쓴다. 이 테스트는 스프링이 하는 것과 같은 바인딩({@link Binder})을 직접 수행해서 그 우선순위를
 * 증명한다.
 */
class BaseballPoolSettingsTest {

    private static final String MAIN_APPLICATION_YML = "src/main/resources/application.yml";
    private static final String HIKARI_PREFIX = "baseball.datasource.hikari";

    @Test
    @DisplayName("운영 yml 이 바인딩되면 코드의 폴백 값을 덮어쓴다 (풀 25, 최소 유휴 5, 연결 타임아웃 30초)")
    void mainYmlOverridesCodeFallbacks() throws IOException {
        MockEnvironment environment = environmentWithMainYml();

        HikariConfig config = bind(environment);

        assertThat(config.getMaximumPoolSize()).isEqualTo(25);
        assertThat(config.getMinimumIdle()).isEqualTo(5);
        assertThat(config.getConnectionTimeout()).isEqualTo(30_000);
        assertThat(config.getValidationTimeout()).isEqualTo(5_000);
        // yml 에 없는 키는 코드 값이 그대로 남는다. 야구 DB 에 못 붙어도 앱이 뜨게 하는 설정이다.
        assertThat(config.getInitializationFailTimeout()).isEqualTo(-1);
    }

    @Test
    @DisplayName("BASEBALL_DB_HIKARI_* 환경변수로 풀 크기·최소 유휴·연결 타임아웃을 바꿀 수 있다")
    void environmentVariablesOverrideYmlDefaults() throws IOException {
        MockEnvironment environment = environmentWithMainYml()
                .withProperty("BASEBALL_DB_HIKARI_MAXIMUM_POOL_SIZE", "40")
                .withProperty("BASEBALL_DB_HIKARI_MINIMUM_IDLE", "8")
                .withProperty("BASEBALL_DB_HIKARI_CONNECTION_TIMEOUT", "10000");

        HikariConfig config = bind(environment);

        assertThat(config.getMaximumPoolSize()).isEqualTo(40);
        assertThat(config.getMinimumIdle()).isEqualTo(8);
        assertThat(config.getConnectionTimeout()).isEqualTo(10_000);
    }

    @Test
    @DisplayName("yml 에 hikari 키가 없는 환경(테스트)에서는 코드의 폴백 값이 실제로 적용된다")
    void codeFallbacksApplyWhenYmlHasNoHikariKeys() {
        // src/test/resources/application.yml 처럼 baseball.datasource.hikari 블록이 없는 상태.
        HikariConfig config = bind(new MockEnvironment());

        assertThat(config.getMinimumIdle()).isZero();
        assertThat(config.getConnectionTimeout()).isEqualTo(3_000);
        assertThat(config.getValidationTimeout()).isEqualTo(2_000);
        assertThat(config.getInitializationFailTimeout()).isEqualTo(-1);
    }

    /** 스프링이 @ConfigurationProperties 빈에 하는 것과 같은 방식으로, 생성된 HikariConfig 위에 바인딩한다. */
    private static HikariConfig bind(MockEnvironment environment) {
        DataSourceProperties properties = new DataSourceProperties();
        properties.setUrl("jdbc:postgresql://localhost:5432/test");
        HikariConfig config = new StadiumPostgresJpaConfig()
                .stadiumHikariConfig(properties, new LinkedHashMap<>());

        Binder.get(environment).bind(HIKARI_PREFIX, Bindable.ofInstance(config));
        return config;
    }

    private static MockEnvironment environmentWithMainYml() throws IOException {
        // src/test/resources/application.yml 이 따로 있어 ClassPathResource 로는 테스트용 설정이 먼저
        // 잡힌다. 운영에 나가는 main 설정을 검증해야 하므로 경로로 직접 읽는다.
        // (Gradle 테스트의 작업 디렉터리는 프로젝트 루트 BEGA_PROJECT 이다.)
        MockEnvironment environment = new MockEnvironment();
        List<PropertySource<?>> documents =
                new YamlPropertySourceLoader().load("application", new FileSystemResource(MAIN_APPLICATION_YML));
        documents.stream()
                // 프로파일 전용 문서(dev/prod/local)는 활성화되지 않으므로 공통 문서만 쓴다.
                .filter(source -> !source.containsProperty("spring.config.activate.on-profile"))
                .forEach(environment.getPropertySources()::addLast);
        return environment;
    }
}
