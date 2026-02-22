-- V63: Create payment_transactions and payout_transactions tables (PostgreSQL)

CREATE TABLE IF NOT EXISTS payment_transactions (
    id BIGSERIAL PRIMARY KEY,
    party_id BIGINT NOT NULL,
    application_id BIGINT NOT NULL,
    buyer_user_id BIGINT NOT NULL,
    seller_user_id BIGINT NOT NULL,
    flow_type VARCHAR(30) NOT NULL,
    order_id VARCHAR(100) NOT NULL,
    payment_key VARCHAR(200) NOT NULL,
    gross_amount INTEGER NOT NULL,
    fee_amount INTEGER NOT NULL DEFAULT 0,
    refund_amount INTEGER NOT NULL DEFAULT 0,
    net_amount INTEGER NOT NULL,
    payment_status VARCHAR(30) NOT NULL,
    settlement_status VARCHAR(30) NOT NULL,
    cancel_reason_type VARCHAR(30),
    cancel_memo VARCHAR(1000),
    refund_policy_applied VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS payout_transactions (
    id BIGSERIAL PRIMARY KEY,
    payment_transaction_id BIGINT NOT NULL,
    seller_id BIGINT NOT NULL,
    requested_amount INTEGER NOT NULL,
    status VARCHAR(30) NOT NULL,
    provider_ref VARCHAR(200),
    requested_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    fail_reason VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_payment_transactions_order_id
    ON payment_transactions(order_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_payment_transactions_payment_key
    ON payment_transactions(payment_key);

CREATE INDEX IF NOT EXISTS idx_payment_transactions_application_id
    ON payment_transactions(application_id);

CREATE INDEX IF NOT EXISTS idx_payment_transactions_status
    ON payment_transactions(payment_status, settlement_status);

CREATE INDEX IF NOT EXISTS idx_payout_transactions_payment_id
    ON payout_transactions(payment_transaction_id);
