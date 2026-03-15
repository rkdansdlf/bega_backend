-- Bootstrap missing stadium guide tables for fresh PostgreSQL schema validation runs.
-- Existing environments with legacy tables are left unchanged via IF NOT EXISTS guards.

CREATE TABLE IF NOT EXISTS stadiums (
    stadium_id VARCHAR(255) PRIMARY KEY,
    stadium_name VARCHAR(255),
    city VARCHAR(255),
    open_year INTEGER,
    capacity INTEGER,
    seating_capacity INTEGER,
    left_fence_m DOUBLE PRECISION,
    center_fence_m DOUBLE PRECISION,
    fence_height_m DOUBLE PRECISION,
    turf_type VARCHAR(255),
    bullpen_type VARCHAR(255),
    homerun_park_factor DOUBLE PRECISION,
    notes VARCHAR(255),
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    lat DOUBLE PRECISION,
    lng DOUBLE PRECISION,
    address VARCHAR(255),
    phone VARCHAR(255),
    team VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS places (
    id BIGSERIAL PRIMARY KEY,
    stadium_id VARCHAR(255) NOT NULL,
    category VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    lat DOUBLE PRECISION NOT NULL,
    lng DOUBLE PRECISION NOT NULL,
    address VARCHAR(255),
    phone VARCHAR(20),
    rating NUMERIC(2,1),
    open_time VARCHAR(50),
    close_time VARCHAR(50),
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT fk_places_stadium
        FOREIGN KEY (stadium_id)
        REFERENCES stadiums (stadium_id)
        ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS user_stadium_favorites (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    stadium_id VARCHAR(20) NOT NULL,
    created_at TIMESTAMP,
    CONSTRAINT uk_user_stadium_favorites_user_stadium UNIQUE (user_id, stadium_id)
);

CREATE INDEX IF NOT EXISTS idx_places_stadium_category
    ON places (stadium_id, category);

CREATE INDEX IF NOT EXISTS idx_user_stadium_favorites_user
    ON user_stadium_favorites (user_id);
