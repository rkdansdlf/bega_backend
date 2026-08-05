package com.example.schema;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * prod 의 Flyway locations 계약을 고정한다.
 *
 * <p>데이터소스와 locations 는 함께 바뀌어야 한다. 기본값이 PostgreSQL 로 넘어가면
 * Oracle 을 보는 배포가 조용히 반대편 마이그레이션을 돌리게 된다.
 *
 * <p>클래스패스가 아니라 소스 파일을 읽는다 — src/test/resources/application.yml 이
 * 테스트 클래스패스에서 main 설정을 가린다.
 */
class FlywayLocationsTest {

    private static final Path APPLICATION_YML =
        Path.of("src", "main", "resources", "application.yml");

    private List<String> locationLines() throws Exception {
        return Files.readAllLines(APPLICATION_YML, StandardCharsets.UTF_8).stream()
            .map(String::trim)
            .filter(line -> line.startsWith("locations:"))
            .collect(Collectors.toList());
    }

    @Test
    void prodKeepsOracleAsTheDefaultSoTheSwitchIsOptIn() throws Exception {
        assertThat(locationLines())
            .contains("locations: ${SPRING_FLYWAY_LOCATIONS:classpath:db/migration/}");
    }

    @Test
    void devStaysPinnedToPostgresql() throws Exception {
        assertThat(locationLines()).contains("locations: classpath:db/migration_postgresql/");
    }
}
