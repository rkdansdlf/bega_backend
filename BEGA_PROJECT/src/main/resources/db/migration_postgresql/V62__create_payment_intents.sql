-- V62: Create payment_intents table for Toss payment intent tracking (PostgreSQL)

CREATE TABLE IF NOT EXISTS payment_intents (
    id BIGSERIAL PRIMARY KEY,
    order_id VARCHAR(100) NOT NULL,
    party_id BIGINT NOT NULL,
    applicant_id BIGINT NOT NULL,
    expected_amount INTEGER NOT NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'KRW',
    payment_type VARCHAR(20) NOT NULL,
    mode VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL,
    payment_key VARCHAR(200),
    failure_code VARCHAR(100),
    failure_message VARCHAR(1000),
    expires_at TIMESTAMPTZ,
    confirmed_at TIMESTAMPTZ,
    canceled_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_payment_intents_order_id
    ON payment_intents(order_id);

CREATE INDEX IF NOT EXISTS idx_payment_intents_party_applicant
    ON payment_intents(party_id, applicant_id);

CREATE INDEX IF NOT EXISTS idx_payment_intents_status_expires
    ON payment_intents(status, expires_at);

CREATE INDEX IF NOT EXISTS idx_payment_intents_payment_key
    ON payment_intents(payment_key);
