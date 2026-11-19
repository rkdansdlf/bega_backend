package com.example.homepage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mock.env.MockEnvironment;

/**
 * 홈 섹션 동시성 한도와 야구 DB 풀 크기의 관계를 검증한다.
 *
 * <p>홈 섹션 하나는 야구 풀의 커넥션 하나를 잡는다. 한도가 풀 크기 이상이면 홈 요청만으로
 * 풀이 바닥나 예측·다이어리·정산이 커넥션을 못 얻는다. 한도를 올릴 때 이 관계가 조용히 깨지지
 * 않도록 application.yml 의 실제 값을 읽어 확인한다.
 */
class HomeSectionConcurrencyConfigTest {

    private static final String MAIN_APPLICATION_YML = "src/main/resources/application.yml";
    private static final String SECTIONS_LIMIT_KEY = "app.home.sections.max-concurrency";
    private static final String BASEBALL_POOL_MAX_KEY = "baseball.datasource.hikari.maximum-pool-size";

    private MockEnvironment environment;

    @BeforeEach
    void loadApplicationYml() throws IOException {
        // MockEnvironment 는 시스템 환경변수를 포함하지 않으므로 기동 환경에 따라 결과가 달라지지 않는다.
        environment = new MockEnvironment();
        // src/test/resources/application.yml 이 따로 있어 ClassPathResource 로는 테스트용 설정이
        // 먼저 잡힌다. 운영에 나가는 main 설정을 검증해야 하므로 경로로 직접 읽는다.
        // (Gradle 테스트의 작업 디렉터리는 프로젝트 루트 BEGA_PROJECT 이다.)
        List<PropertySource<?>> documents =
                new YamlPropertySourceLoader().load("application", new FileSystemResource(MAIN_APPLICATION_YML));
        documents.forEach(environment.getPropertySources()::addLast);
    }

    @Test
    @DisplayName("application.yml 의 기본 한도는 코드 기본값과 같다")
    void ymlDefaultMatchesCodeDefault() {
        Integer ymlLimit = environment.getProperty(SECTIONS_LIMIT_KEY, Integer.class);

        assertThat(ymlLimit).isEqualTo(HomePageFacadeService.DEFAULT_SECTION_MAX_CONCURRENCY);
    }

    @Test
    @DisplayName("홈 섹션 한도는 야구 풀 최대 크기보다 작아 다른 기능 몫의 커넥션을 남긴다")
    void sectionLimitLeavesHeadroomInBaseballPool() {
        Integer sectionLimit = environment.getProperty(SECTIONS_LIMIT_KEY, Integer.class);
        Integer baseballPoolMax = environment.getProperty(BASEBALL_POOL_MAX_KEY, Integer.class);

        assertThat(sectionLimit).as(SECTIONS_LIMIT_KEY).isNotNull();
        assertThat(baseballPoolMax).as(BASEBALL_POOL_MAX_KEY).isNotNull();
        assertThat(sectionLimit)
                .as("홈 섹션 한도(%d)는 야구 풀 최대 크기(%d)보다 작아야 한다", sectionLimit, baseballPoolMax)
                .isLessThan(baseballPoolMax);
    }
}
