-- V86: Create mate_seller_payout_profiles table (PostgreSQL).
-- PostgreSQL equivalent of Oracle V86.
-- V69 already created this table; CREATE TABLE IF NOT EXISTS is a no-op on existing envs.

CREATE TABLE IF NOT EXISTS mate_seller_payout_profiles (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    provider VARCHAR(20) NOT NULL,
    provider_seller_id VARCHAR(120) NOT NULL,
    kyc_status VARCHAR(50),
    metadata_json TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_mate_seller_payout_profiles_user_provider
    ON mate_seller_payout_profiles (user_id, provider);

CREATE UNIQUE INDEX IF NOT EXISTS uq_mate_seller_payout_profiles_provider_seller
    ON mate_seller_payout_profiles (provider, provider_seller_id);
