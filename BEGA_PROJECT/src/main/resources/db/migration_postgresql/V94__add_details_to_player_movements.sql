-- Add missing details column for PlayerMovement mapping
DO $$
BEGIN
    IF to_regclass('public.player_movements') IS NULL THEN
        RETURN;
    END IF;

    ALTER TABLE player_movements
    ADD COLUMN IF NOT EXISTS details text;
END $$;
