-- BaseballFlywayMigrationConfigTest 전용.
-- 배선(locations 반영, 전용 이력 테이블 사용, migrate 실제 수행)만 검증하므로
-- 어떤 엔진에서나 도는 평범한 DDL 을 쓴다. 운영 세트(db/migration_baseball/)는
-- PostgreSQL 의 DO $$ 블록을 쓰기 때문에 H2 에서 파싱되지 않는다 -- 그것은
-- 이 설정의 문제가 아니라 방언 차이이므로 여기서 검증할 대상이 아니다.
CREATE TABLE baseball_flyway_wiring_probe (
    id INTEGER NOT NULL PRIMARY KEY
);
