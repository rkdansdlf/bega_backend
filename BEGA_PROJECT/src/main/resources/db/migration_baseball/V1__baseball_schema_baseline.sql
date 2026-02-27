-- ============================================================
-- V1: baseball PostgreSQL 전용 스키마 베이스라인
--
-- 목적: prod baseball PostgreSQL에 필요한 테이블만 선언.
-- 설계: 모든 DDL은 IF NOT EXISTS 기반 idempotent.
--       기존 테이블이 있으면 skip, 없으면 생성.
--       (기존 migration_postgresql Flyway가 생성해 둔 테이블과 충돌 없음)
-- ============================================================

-- ─────────────────────────────────────────────
-- 스타디움
-- ─────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS stadiums (
    stadium_id          VARCHAR(255)     NOT NULL,
    stadium_name        VARCHAR(255),
    city                VARCHAR(255),
    open_year           INTEGER,
    capacity            INTEGER,
    seating_capacity    INTEGER,
    left_fence_m        DOUBLE PRECISION,
    center_fence_m      DOUBLE PRECISION,
    fence_height_m      DOUBLE PRECISION,
    turf_type           VARCHAR(255),
    bullpen_type        VARCHAR(255),
    homerun_park_factor DOUBLE PRECISION,
    notes               VARCHAR(255),
    lat                 DOUBLE PRECISION,
    lng                 DOUBLE PRECISION,
    address             VARCHAR(255),
    phone               VARCHAR(255),
    team                VARCHAR(255),
    created_at          TIMESTAMP,
    updated_at          TIMESTAMP,
    CONSTRAINT pk_stadiums PRIMARY KEY (stadium_id)
);

CREATE TABLE IF NOT EXISTS places (
    id          BIGSERIAL        NOT NULL,
    stadium_id  VARCHAR(255)     NOT NULL,
    category    VARCHAR(50)      NOT NULL,
    name        VARCHAR(100)     NOT NULL,
    description VARCHAR(255),
    lat         DOUBLE PRECISION NOT NULL,
    lng         DOUBLE PRECISION NOT NULL,
    address     VARCHAR(255),
    phone       VARCHAR(20),
    rating      DECIMAL(2, 1),
    open_time   VARCHAR(50),
    close_time  VARCHAR(50),
    created_at  TIMESTAMP,
    updated_at  TIMESTAMP,
    CONSTRAINT pk_places PRIMARY KEY (id),
    CONSTRAINT fk_places_stadium FOREIGN KEY (stadium_id) REFERENCES stadiums (stadium_id)
);

-- ─────────────────────────────────────────────
-- 팀 / 프랜차이즈
-- ─────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS team_franchises (
    id            SERIAL       NOT NULL,
    name          VARCHAR(50)  NOT NULL,
    original_code VARCHAR(10)  NOT NULL,
    current_code  VARCHAR(10)  NOT NULL,
    metadata_json TEXT,
    web_url       VARCHAR(255),
    created_at    TIMESTAMP,
    updated_at    TIMESTAMP,
    CONSTRAINT pk_team_franchises PRIMARY KEY (id),
    CONSTRAINT uq_team_franchises_original_code UNIQUE (original_code)
);

CREATE TABLE IF NOT EXISTS teams (
    team_id         VARCHAR(255) NOT NULL,
    team_name       VARCHAR(255) NOT NULL,
    team_short_name VARCHAR(255) NOT NULL,
    city            VARCHAR(30)  NOT NULL,
    stadium_name    VARCHAR(50),
    founded_year    INTEGER,
    color           VARCHAR(255),
    franchise_id    INTEGER,
    is_active       BOOLEAN,
    aliases         TEXT,
    created_at      TIMESTAMP    NOT NULL,
    updated_at      TIMESTAMP    NOT NULL,
    CONSTRAINT pk_teams PRIMARY KEY (team_id),
    CONSTRAINT fk_teams_franchise FOREIGN KEY (franchise_id) REFERENCES team_franchises (id)
);

CREATE TABLE IF NOT EXISTS team_history (
    id           SERIAL      NOT NULL,
    franchise_id INTEGER     NOT NULL,
    season       INTEGER     NOT NULL,
    team_name    VARCHAR(50) NOT NULL,
    team_code    VARCHAR(10) NOT NULL,
    logo_url     VARCHAR(255),
    ranking      INTEGER,
    stadium      VARCHAR(50),
    city         VARCHAR(30),
    color        VARCHAR(50),
    created_at   TIMESTAMP,
    updated_at   TIMESTAMP,
    CONSTRAINT pk_team_history PRIMARY KEY (id),
    CONSTRAINT uq_team_history_season_code UNIQUE (season, team_code),
    CONSTRAINT fk_team_history_franchise FOREIGN KEY (franchise_id) REFERENCES team_franchises (id)
);

-- ─────────────────────────────────────────────
-- 선수 / 수상
-- ─────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS player_movements (
    id            BIGSERIAL    NOT NULL,
    movement_date DATE         NOT NULL,
    section       VARCHAR(50)  NOT NULL,
    team_code     VARCHAR(20)  NOT NULL,
    player_name   VARCHAR(100) NOT NULL,
    remarks       TEXT,
    created_at    TIMESTAMP    NOT NULL,
    updated_at    TIMESTAMP    NOT NULL,
    CONSTRAINT pk_player_movements PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS awards (
    id          BIGSERIAL    NOT NULL,
    award_type  VARCHAR(255) NOT NULL,
    player_name VARCHAR(255) NOT NULL,
    award_year  INTEGER      NOT NULL,
    position    VARCHAR(255),
    CONSTRAINT pk_awards PRIMARY KEY (id)
);

-- ─────────────────────────────────────────────
-- 경기 데이터
-- ─────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS game (
    id            BIGSERIAL   NOT NULL,
    game_id       VARCHAR(20) NOT NULL,
    game_date     DATE,
    stadium       VARCHAR(50),
    home_team     VARCHAR(20),
    away_team     VARCHAR(20),
    home_score    INTEGER,
    away_score    INTEGER,
    winning_team  VARCHAR(20),
    winning_score INTEGER,
    season_id     INTEGER,
    stadium_id    VARCHAR(50),
    game_status   VARCHAR(20),
    is_dummy      BOOLEAN,
    home_pitcher  VARCHAR(30),
    away_pitcher  VARCHAR(30),
    CONSTRAINT pk_game PRIMARY KEY (id),
    CONSTRAINT uq_game_game_id UNIQUE (game_id)
);

CREATE INDEX IF NOT EXISTS idx_game_date        ON game (game_date);
CREATE INDEX IF NOT EXISTS idx_game_season_id   ON game (season_id);
CREATE INDEX IF NOT EXISTS idx_game_teams       ON game (home_team, away_team);
CREATE INDEX IF NOT EXISTS idx_game_range_lookup ON game (game_date, game_status, season_id);

CREATE TABLE IF NOT EXISTS game_metadata (
    game_id           VARCHAR(20) NOT NULL,
    stadium_code      VARCHAR(20),
    stadium_name      VARCHAR(50),
    attendance        INTEGER,
    start_time        TIME,
    end_time          TIME,
    game_time_minutes INTEGER,
    weather           VARCHAR(50),
    source_payload    VARCHAR(255),
    created_at        TIMESTAMP,
    updated_at        TIMESTAMP,
    CONSTRAINT pk_game_metadata PRIMARY KEY (game_id)
);

CREATE TABLE IF NOT EXISTS game_summary (
    id           SERIAL      NOT NULL,
    game_id      VARCHAR(20) NOT NULL,
    summary_type VARCHAR(50),
    player_id    INTEGER,
    player_name  VARCHAR(50),
    detail_text  VARCHAR(255),
    created_at   TIMESTAMP,
    updated_at   TIMESTAMP,
    CONSTRAINT pk_game_summary PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS game_inning_scores (
    id        SERIAL      NOT NULL,
    game_id   VARCHAR(20) NOT NULL,
    team_side VARCHAR(10) NOT NULL,
    team_code VARCHAR(10),
    inning    INTEGER     NOT NULL,
    runs      INTEGER,
    is_extra  BOOLEAN,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT pk_game_inning_scores PRIMARY KEY (id)
);

-- ─────────────────────────────────────────────
-- 티켓 검증
-- ─────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS ticket_verifications (
    token          VARCHAR(36) NOT NULL,
    ticket_date    VARCHAR(255),
    ticket_stadium VARCHAR(255),
    home_team      VARCHAR(255),
    away_team      VARCHAR(255),
    game_id        BIGINT,
    consumed       BOOLEAN     NOT NULL DEFAULT false,
    expires_at     TIMESTAMP   NOT NULL,
    created_at     TIMESTAMP   NOT NULL,
    CONSTRAINT pk_ticket_verifications PRIMARY KEY (token)
);

-- ─────────────────────────────────────────────
-- 점수 이벤트
-- ─────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS score_events (
    id            BIGSERIAL    NOT NULL,
    user_id       BIGINT       NOT NULL,
    prediction_id BIGINT,
    game_id       VARCHAR(50),
    event_type    VARCHAR(50)  NOT NULL,
    base_score    INTEGER      NOT NULL,
    multiplier    DECIMAL(5,2) NOT NULL DEFAULT 1.00,
    final_score   INTEGER      NOT NULL,
    streak_count  INTEGER               DEFAULT 0,
    description   VARCHAR(500),
    created_at    TIMESTAMP    NOT NULL,
    CONSTRAINT pk_score_events PRIMARY KEY (id)
);
