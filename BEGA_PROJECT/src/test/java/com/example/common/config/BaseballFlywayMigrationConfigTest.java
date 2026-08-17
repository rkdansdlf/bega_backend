package com.example.common.config;

import java.util.UUID;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BaseballFlywayMigrationConfig tests")
class BaseballFlywayMigrationConfigTest {

    private static final String PG_LOCATIONS = "classpath:db/migration_baseball/";

    private DataSource h2DataSource() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl("jdbc:h2:mem:baseball-flyway-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        ds.setUsername("sa");
        ds.setPassword("");
        return ds;
    }

    private boolean historyTableExists(DataSource ds) {
        Integer count = new JdbcTemplate(ds).queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE UPPER(table_name) = ?",
                Integer.class,
                BaseballFlywayMigrationConfig.HISTORY_TABLE.toUpperCase());
        return count != null && count > 0;
    }

    @Test
    @DisplayName("기본값은 비활성이며 스키마를 건드리지 않는다")
    void disabledByDefaultDoesNotTouchTheSchema() {
        DataSource ds = h2DataSource();

        Flyway flyway = new BaseballFlywayMigrationConfig()
                .baseballFlyway(ds, false, PG_LOCATIONS);

        assertThat(flyway).isNotNull();
        // 배선만 하고 migrate 를 부르지 않았으므로 이력 테이블조차 생기지 않아야 한다.
        // 운영 야구 DB 에는 미적용 마이그레이션이 남아 있어, 배포만으로 스키마가
        // 바뀌면 그것이 곧 예고 없는 변경이 된다.
        assertThat(historyTableExists(ds)).isFalse();
    }

    @Test
    @DisplayName("앱 이력과 분리된 flyway_baseball_schema_history 를 쓴다")
    void usesADedicatedHistoryTable() {
        assertThat(BaseballFlywayMigrationConfig.HISTORY_TABLE)
                .isEqualTo("flyway_baseball_schema_history")
                .isNotEqualTo("flyway_schema_history");
    }

    @Test
    @DisplayName("활성화하면 지정한 locations 를 적용하고 전용 이력 테이블에 기록한다")
    void enabledAppliesMigrationsIntoTheDedicatedHistoryTable() {
        DataSource ds = h2DataSource();

        // 운영 세트가 아니라 엔진 중립 테스트 세트를 쓴다. 여기서 검증할 것은
        // 배선(locations 반영 / 전용 이력 테이블 / migrate 실제 수행)이지 SQL 방언이 아니다.
        new BaseballFlywayMigrationConfig()
                .baseballFlyway(ds, true, "classpath:db/migration_baseball_test/");

        assertThat(historyTableExists(ds)).isTrue();

        // 이력 테이블 내용은 직접 세지 않는다. Flyway 가 엔진별로 식별자를 인용해
        // 만들기 때문에(H2 에서는 소문자 따옴표) 언인용 조회가 깨진다. 적용 여부는
        // 해당 세트가 만드는 객체의 존재로 확인하는 편이 엔진에 흔들리지 않는다.
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        Integer probe = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE UPPER(table_name) = 'BASEBALL_FLYWAY_WIRING_PROBE'",
                Integer.class);
        assertThat(probe).isEqualTo(1);
    }
}
