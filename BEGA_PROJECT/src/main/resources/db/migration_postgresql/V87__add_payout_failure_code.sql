-- V87: Add failure_code to payout_transactions (PostgreSQL).
-- PostgreSQL equivalent of Oracle V87.
-- V70 already added this column; ADD COLUMN IF NOT EXISTS is a no-op on existing envs.

ALTER TABLE IF EXISTS payout_transactions
    ADD COLUMN IF NOT EXISTS failure_code VARCHAR(100);
