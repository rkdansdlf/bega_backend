-- V65: Add payout retry metadata columns (PostgreSQL)

ALTER TABLE payout_transactions
    ADD COLUMN IF NOT EXISTS retry_count INTEGER DEFAULT 0 NOT NULL,
    ADD COLUMN IF NOT EXISTS last_retry_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS next_retry_at TIMESTAMPTZ;

UPDATE payout_transactions
SET retry_count = 0
WHERE retry_count IS NULL;

