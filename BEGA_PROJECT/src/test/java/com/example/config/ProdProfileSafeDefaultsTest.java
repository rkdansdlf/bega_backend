package com.example.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.mock.env.MockEnvironment;

/**
 * 실제 application.yml 을 프로필별로 읽어, 운영 기본값이 안전하게 유지되는지 고정한다.
 *
 * <p>prod 블록의 필수 환경 변수 placeholder(${X:?...})는 해당 속성을 읽을 때만 평가되므로,
 * 아래 속성들만 조회하는 이 테스트는 운영 시크릿 없이도 실행된다.
 */
@DisplayName("프로필별 application.yml 안전 기본값")
class ProdProfileSafeDefaultsTest {

    private static final String MAIN_APPLICATION_YML = "file:src/main/resources/application.yml";

    private static MockEnvironment load(String profile) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profile);
        // src/test/resources/application.yml 이 클래스패스에서 메인 파일을 가리므로, 운영에 실제로 배포되는
        // 메인 설정 파일을 경로로 직접 읽는다. (이걸 빼면 테스트용 값을 읽어 아무것도 검증하지 못한다.)
        environment.withProperty("spring.config.location", MAIN_APPLICATION_YML);
        // 로컬 .env 파일이 결과에 섞이지 않게 한다.
        environment.withProperty("spring.config.import", "");
        ConfigDataEnvironmentPostProcessor.applyTo(environment);
        return environment;
    }

    @Test
    @DisplayName("전제 확인: 테스트용 yml 이 아니라 메인 application.yml 을 읽고 있다")
    void readsMainApplicationYmlNotTestResource() {
        // 테스트용 yml 에는 없고 메인 yml 공통 문서에만 있는 값
        assertThat(load("prod").getProperty("management.endpoints.web.base-path")).isEqualTo("/actuator");
    }

    @Test
    @DisplayName("JobRunr 대시보드는 prod 에서 기본적으로 꺼져 있다")
    void jobrunrDashboardIsDisabledByDefaultInProd() {
        assertThat(load("prod").getProperty("jobrunr.dashboard.enabled", Boolean.class)).isFalse();
    }

    @Test
    @DisplayName("Swagger UI 와 API 문서는 prod 에서 꺼져 있다")
    void swaggerIsDisabledInProd() {
        MockEnvironment prod = load("prod");

        assertThat(prod.getProperty("springdoc.swagger-ui.enabled", Boolean.class)).isFalse();
        assertThat(prod.getProperty("springdoc.api-docs.enabled", Boolean.class)).isFalse();
    }

    @Test
    @DisplayName("헬스 상세는 prod 에서 노출되지 않는다")
    void healthDetailsAreHiddenInProd() {
        assertThat(load("prod").getProperty("management.endpoint.health.show-details")).isEqualTo("never");
    }

    @Test
    @DisplayName("Prometheus 엔드포인트 공개와 dev 시드 데이터는 prod 에서 기본적으로 꺼져 있다")
    void publicPrometheusAndDevDataAreOffByDefaultInProd() {
        MockEnvironment prod = load("prod");

        assertThat(prod.getProperty("app.observability.public-prometheus-endpoint", Boolean.class)).isFalse();
        assertThat(prod.getProperty("app.dev-data.enabled", Boolean.class)).isFalse();
    }

    @Test
    @DisplayName("대조군: dev 프로필은 대시보드와 시드 데이터가 켜져 있어, 운영에서 프로필 선택이 중요하다")
    void devProfileDiffersFromProd() {
        MockEnvironment dev = load("dev");

        assertThat(dev.getProperty("jobrunr.dashboard.enabled", Boolean.class)).isTrue();
        assertThat(dev.getProperty("app.dev-data.enabled", Boolean.class)).isTrue();
    }
}
