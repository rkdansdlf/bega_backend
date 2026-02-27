-- V95: Create user_policy_consents table for required policy version tracking (PostgreSQL)

CREATE TABLE IF NOT EXISTS user_policy_consents (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    policy_type VARCHAR(40) NOT NULL,
    policy_version VARCHAR(20) NOT NULL,
    consented_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    consent_method VARCHAR(20) NOT NULL,
    consent_ip VARCHAR(64),
    consent_user_agent TEXT
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_user_policy_consents_user_policy_version
    ON user_policy_consents(user_id, policy_type, policy_version);

CREATE INDEX IF NOT EXISTS ix_user_policy_consents_user_id
    ON user_policy_consents(user_id);

