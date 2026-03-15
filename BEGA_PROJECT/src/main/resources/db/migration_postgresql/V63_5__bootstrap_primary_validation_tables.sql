-- Bootstrap missing primary validation tables for fresh PostgreSQL schema validation runs.
-- Existing environments with legacy tables are left unchanged via IF NOT EXISTS guards.

CREATE TABLE IF NOT EXISTS parties (
    id BIGSERIAL PRIMARY KEY,
    hostid BIGINT NOT NULL,
    hostname VARCHAR(50) NOT NULL,
    hostbadge VARCHAR(20) NOT NULL DEFAULT 'NEW',
    host_profile_image_url VARCHAR(2048),
    teamid VARCHAR(20) NOT NULL,
    gamedate DATE NOT NULL,
    gametime TIME NOT NULL,
    stadium VARCHAR(100) NOT NULL,
    host_favorite_team VARCHAR(20),
    hometeam VARCHAR(20) NOT NULL,
    awayteam VARCHAR(20) NOT NULL,
    section VARCHAR(50) NOT NULL,
    maxparticipants INTEGER NOT NULL,
    currentparticipants INTEGER NOT NULL DEFAULT 1,
    description VARCHAR(1000) NOT NULL,
    search_text VARCHAR(2000),
    ticketverified BOOLEAN NOT NULL DEFAULT FALSE,
    ticketimageurl VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reservation_number VARCHAR(50),
    price INTEGER,
    ticketprice INTEGER,
    host_last_read_chat_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    createdat TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updatedat TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS party_applications (
    id BIGSERIAL PRIMARY KEY,
    partyid BIGINT NOT NULL,
    applicantid BIGINT NOT NULL,
    applicant_name VARCHAR(50) NOT NULL,
    applicant_badge VARCHAR(20) NOT NULL DEFAULT 'NEW',
    applicant_rating DOUBLE PRECISION NOT NULL DEFAULT 5.0,
    message VARCHAR(500) NOT NULL,
    deposit_amount INTEGER NOT NULL DEFAULT 0,
    is_paid BOOLEAN NOT NULL DEFAULT FALSE,
    is_approved BOOLEAN NOT NULL DEFAULT FALSE,
    is_rejected BOOLEAN NOT NULL DEFAULT FALSE,
    payment_type VARCHAR(20) NOT NULL DEFAULT 'DEPOSIT',
    ticket_verified BOOLEAN DEFAULT FALSE,
    ticket_image_url VARCHAR(500),
    payment_key VARCHAR(200),
    order_id VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    approved_at TIMESTAMPTZ,
    rejected_at TIMESTAMPTZ,
    response_deadline TIMESTAMPTZ,
    last_read_chat_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_pa_order_id UNIQUE (order_id),
    CONSTRAINT uk_pa_payment_key UNIQUE (payment_key)
);

CREATE TABLE IF NOT EXISTS party_reviews (
    id BIGSERIAL PRIMARY KEY,
    party_id BIGINT NOT NULL,
    reviewer_id BIGINT NOT NULL,
    reviewee_id BIGINT NOT NULL,
    rating INTEGER NOT NULL,
    review_comment VARCHAR(200),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_party_reviews_triplet UNIQUE (party_id, reviewer_id, reviewee_id)
);

CREATE TABLE IF NOT EXISTS predictions (
    id BIGSERIAL PRIMARY KEY,
    game_id VARCHAR(255) NOT NULL,
    user_id BIGINT,
    voted_team VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_predictions_user_game UNIQUE (user_id, game_id)
);

CREATE TABLE IF NOT EXISTS ranking_predictions (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(255),
    season_year INTEGER NOT NULL,
    prediction_data TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS vote_final_results (
    game_id VARCHAR(255) PRIMARY KEY,
    final_votes_a INTEGER NOT NULL DEFAULT 0,
    final_votes_b INTEGER NOT NULL DEFAULT 0,
    final_winner VARCHAR(50),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS chat_messages (
    id BIGSERIAL PRIMARY KEY,
    party_id BIGINT NOT NULL,
    sender_id BIGINT NOT NULL,
    sender_name VARCHAR(50) NOT NULL,
    message VARCHAR(1000) NOT NULL,
    image_url VARCHAR(2048),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS check_in_records (
    id BIGSERIAL PRIMARY KEY,
    party_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    location VARCHAR(100) NOT NULL,
    checked_in_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_parties_status_gamedate_bootstrap
    ON parties (status, gamedate DESC);

CREATE INDEX IF NOT EXISTS idx_party_applications_partyid
    ON party_applications (partyid);

CREATE INDEX IF NOT EXISTS idx_predictions_user_game_bootstrap
    ON predictions (user_id, game_id);

CREATE INDEX IF NOT EXISTS idx_predictions_game_id_bootstrap
    ON predictions (game_id);

CREATE INDEX IF NOT EXISTS idx_ranking_predictions_user_season
    ON ranking_predictions (user_id, season_year);

CREATE INDEX IF NOT EXISTS idx_chat_messages_party_created_at
    ON chat_messages (party_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_check_in_records_party_user
    ON check_in_records (party_id, user_id);
