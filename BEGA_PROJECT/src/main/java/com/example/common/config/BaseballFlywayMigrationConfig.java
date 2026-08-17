package com.example.common.config;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.extern.slf4j.Slf4j;

/**
 * 야구 데이터소스 전용 Flyway.
 *
 * <p>Spring Boot 의 자동 구성 Flyway 는 primary 데이터소스 하나만 관리한다. 야구 스키마는
 * 그동안 <em>자동 실행 경로가 아예 없었다</em> — {@code flyway_baseball_schema_history} 를
 * 보면 2026-02-26~27 에 V1~V4 가 한 번 수동 적용된 것이 전부고, 그 뒤 추가된 V5·V6 는
 * 적용된 적이 없다. 실제 스키마 대부분은 앱의 {@code migration_postgresql/} 세트가 만들었다
 * (야구 DB 가 예전에 앱 DB 였다). 이 빈이 그 공백을 메운다.
 *
 * <p><b>기본값은 비활성이다.</b> 이 코드를 배포하는 것만으로는 아무 일도 일어나지 않는다.
 * 운영 야구 DB 에는 미적용 마이그레이션이 남아 있어, 배선과 동시에 자동 적용되면 그것이
 * 곧 예고 없는 스키마 변경이 된다. 활성화는 별도의 의도적 행위여야 한다.
 *
 * <p><b>크롤러 영역은 만들지 않는다.</b> 야구 테이블(game, teams, player_movements 등)의
 * 소유자는 크롤러 프로젝트(KBO_playwright)이고, 그쪽이 SQLAlchemy ORM 으로 ADB 스키마를
 * 직접 만든다({@code apply_oracle_migrations}). 우리가 같은 테이블을 Flyway 로 또 선언하면
 * 하나의 DB 를 두고 두 개의 스키마 정의가 싸운다. 그래서
 * {@code db/migration_baseball_oracle/} 은 우리 소유인 stadiums·places 만 만든다.
 * 소유권 판정은 {@code contracts/baseball-schema/} 계약에서 끌어온다.
 *
 * <p>활성화 시 반드시 함께 확인할 것:
 * <ul>
 *   <li>{@code baseball.flyway.locations} 가 데이터소스 엔진과 맞는지.
 *       PostgreSQL 은 {@code db/migration_baseball/}, Oracle(ADB) 은
 *       {@code db/migration_baseball_oracle/} 이다. 엇갈리면 다른 엔진의 DDL 이 돌아
 *       문법 오류로 죽는다.</li>
 *   <li>전환 직전에 {@code docker exec printenv} 로 컨테이너가 값을 실제로 받았는지.
 *       선언한 것과 실행 환경에 도달한 것은 다르다(2026-08 Oracle 전환 1·2차 실패의 원인).</li>
 * </ul>
 */
@Slf4j
@Configuration
public class BaseballFlywayMigrationConfig {

    /** 앱의 flyway_schema_history 와 반드시 분리한다. 두 이력이 섞이면 복구가 어렵다. */
    static final String HISTORY_TABLE = "flyway_baseball_schema_history";

    @Bean
    public Flyway baseballFlyway(
            @Qualifier("stadiumDataSource") DataSource baseballDataSource,
            @Value("${baseball.flyway.enabled:false}") boolean enabled,
            @Value("${baseball.flyway.locations:classpath:db/migration_baseball/}") String locations) {

        Flyway flyway = Flyway.configure()
                .dataSource(baseballDataSource)
                .locations(locations.split(","))
                .table(HISTORY_TABLE)
                // 운영 DB 에는 이미 baseline 행이 있다. 빈 ADB 에서는 이 옵션이
                // 첫 마이그레이션을 baseline 으로 만들지 않도록 baselineVersion 을 0 으로 둔다.
                .baselineOnMigrate(true)
                .baselineVersion("0")
                // V1~V4 는 2026-02-27 이후 수정된 적이 없어 체크섬이 그대로다.
                // 검증을 끄면 그 사실이 깨져도 조용히 지나간다.
                .validateOnMigrate(true)
                .outOfOrder(false)
                .cleanDisabled(true)
                .load();

        if (!enabled) {
            log.info(
                    "baseball.flyway.skipped enabled=false locations={} table={} "
                            + "(활성화하려면 baseball.flyway.enabled=true)",
                    locations,
                    HISTORY_TABLE);
            return flyway;
        }

        MigrateResult result = flyway.migrate();
        log.info(
                "baseball.flyway.migrated locations={} table={} initialVersion={} targetVersion={} applied={}",
                locations,
                HISTORY_TABLE,
                result.initialSchemaVersion,
                result.targetSchemaVersion,
                result.migrationsExecuted);
        return flyway;
    }
}
