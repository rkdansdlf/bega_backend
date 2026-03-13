-- V70: Add failure_code to payout_transactions (PostgreSQL)

ALTER TABLE payout_transactions
    ADD COLUMN IF NOT EXISTS failure_code VARCHAR(100);

