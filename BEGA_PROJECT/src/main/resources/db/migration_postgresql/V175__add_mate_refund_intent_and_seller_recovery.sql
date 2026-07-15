-- Persist immutable Mate refund intent and seller payout recovery debt (PostgreSQL).

ALTER TABLE IF EXISTS payment_transactions
    ADD COLUMN IF NOT EXISTS requested_refund_amount INTEGER,
    ADD COLUMN IF NOT EXISTS requested_fee_amount INTEGER,
    ADD COLUMN IF NOT EXISTS cancellation_requested_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS provider_reconciled_at TIMESTAMPTZ;

ALTER TABLE IF EXISTS payout_transactions
    ADD COLUMN IF NOT EXISTS recovery_offset_amount INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS recovery_offset_reserved_at TIMESTAMPTZ;

CREATE UNIQUE INDEX IF NOT EXISTS uq_payout_payment_tx
    ON payout_transactions(payment_transaction_id);

CREATE TABLE IF NOT EXISTS seller_payout_recoveries (
    id BIGSERIAL PRIMARY KEY,
    source_payment_transaction_id BIGINT NOT NULL,
    payout_transaction_id BIGINT,
    seller_user_id BIGINT NOT NULL,
    original_paid_amount INTEGER NOT NULL,
    target_net_amount INTEGER NOT NULL,
    recovery_amount INTEGER NOT NULL,
    recovered_amount INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_spr_source_payment
    ON seller_payout_recoveries(source_payment_transaction_id);

CREATE INDEX IF NOT EXISTS idx_spr_seller_status
    ON seller_payout_recoveries(seller_user_id, status, id);
