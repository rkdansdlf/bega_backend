-- Add structured detail fields for offseason movement collection.

DO $$
BEGIN
    IF to_regclass('public.player_movements') IS NULL THEN
        RETURN;
    END IF;

    EXECUTE '
        ALTER TABLE player_movements
            ADD COLUMN IF NOT EXISTS summary VARCHAR(300),
            ADD COLUMN IF NOT EXISTS contract_term VARCHAR(100),
            ADD COLUMN IF NOT EXISTS contract_value VARCHAR(120),
            ADD COLUMN IF NOT EXISTS option_details VARCHAR(300),
            ADD COLUMN IF NOT EXISTS counterparty_team VARCHAR(50),
            ADD COLUMN IF NOT EXISTS counterparty_details VARCHAR(500),
            ADD COLUMN IF NOT EXISTS source_label VARCHAR(100),
            ADD COLUMN IF NOT EXISTS source_url VARCHAR(500),
            ADD COLUMN IF NOT EXISTS announced_at TIMESTAMP
    ';
END;
$$;
