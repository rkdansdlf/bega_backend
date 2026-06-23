-- V151: Create daily mate search term aggregate table (PostgreSQL)

CREATE TABLE IF NOT EXISTS mate_search_terms_daily (
    id BIGSERIAL PRIMARY KEY,
    search_date DATE NOT NULL,
    normalized_term VARCHAR(50) NOT NULL,
    display_term VARCHAR(50) NOT NULL,
    search_count BIGINT NOT NULL DEFAULT 1,
    last_searched_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_mate_search_daily_date_term
    ON mate_search_terms_daily (search_date, normalized_term);

CREATE INDEX IF NOT EXISTS idx_mate_search_daily_rank
    ON mate_search_terms_daily (search_date, search_count, last_searched_at);
