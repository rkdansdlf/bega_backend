ALTER TABLE IF EXISTS parties
    ADD COLUMN IF NOT EXISTS reservation_deposit_amount INTEGER;

COMMENT ON COLUMN parties.reservation_deposit_amount IS 'Optional per-person reservation deposit shown on mate party detail';
