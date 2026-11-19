package com.example.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.stadium.config.StadiumPostgresJpaConfig;
import com.zaxxer.hikari.HikariConfig;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.mock.env.MockEnvironment;

/**
 * 두 Hikari 풀이 서로 다른 고정 이름을 갖는지 검증한다.
 *
 * <p>이름을 지정하지 않으면 생성 순서대로 HikariPool-1, -2 가 붙는다. 그러면 Prometheus 의
 * {@code pool} 라벨(알림 규칙·Grafana 가 사용)로 어느 DB 의 풀인지 알 수 없고, 빈 생성 순서가
 * 바뀌면 이름과 DB 의 대응도 조용히 뒤바뀐다.
 */
class HikariPoolNamingTest {

    private static final String POSTGRES_URL = "jdbc:postgresql://localhost:5432/test";

    @Test
    @DisplayName("primary 풀은 'primary' 라는 고정 이름을 갖는다")
    void primaryPoolHasFixedName() {
        PrimaryOracleJpaConfig config = new PrimaryOracleJpaConfig(new MockEnvironment());

        HikariConfig hikariConfig = config.primaryHikariConfig(properties(), new LinkedHashMap<>());

        assertThat(hikariConfig.getPoolName()).isEqualTo("primary");
    }

    @Test
    @DisplayName("야구 풀은 'baseball' 이라는 고정 이름을 갖는다")
    void baseballPoolHasFixedName() {
        StadiumPostgresJpaConfig config = new StadiumPostgresJpaConfig();

        HikariConfig hikariConfig = config.stadiumHikariConfig(properties(), new LinkedHashMap<>());

        assertThat(hikariConfig.getPoolName()).isEqualTo("baseball");
    }

    @Test
    @DisplayName("두 풀의 이름은 서로 달라야 한다")
    void poolNamesAreDistinct() {
        assertThat(PrimaryOracleJpaConfig.PRIMARY_POOL_NAME)
                .isNotEqualTo(StadiumPostgresJpaConfig.BASEBALL_POOL_NAME);
    }

    private static DataSourceProperties properties() {
        DataSourceProperties properties = new DataSourceProperties();
        properties.setUrl(POSTGRES_URL);
        properties.setUsername("test");
        properties.setPassword("test");
        return properties;
    }
}
