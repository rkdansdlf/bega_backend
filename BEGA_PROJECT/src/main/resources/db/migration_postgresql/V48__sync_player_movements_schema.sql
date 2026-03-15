-- Align player_movements table with PlayerMovement entity mapping.

DO $$
BEGIN
    IF to_regclass('public.player_movements') IS NULL THEN
        RETURN;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'player_movements'
          AND column_name = 'date'
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'player_movements'
          AND column_name = 'movement_date'
    ) THEN
        EXECUTE 'ALTER TABLE player_movements RENAME COLUMN date TO movement_date';
    END IF;
END;
$$;

DO $$
BEGIN
    IF to_regclass('public.player_movements') IS NULL THEN
        RETURN;
    END IF;

    ALTER TABLE player_movements
        ADD COLUMN IF NOT EXISTS movement_date DATE;
END $$;

DO $$
BEGIN
    IF to_regclass('public.player_movements') IS NULL THEN
        RETURN;
    END IF;

    UPDATE player_movements
    SET movement_date = COALESCE(movement_date, CURRENT_DATE)
    WHERE movement_date IS NULL;
END $$;

DO $$
BEGIN
    IF to_regclass('public.player_movements') IS NULL THEN
        RETURN;
    END IF;

    ALTER TABLE player_movements
        ALTER COLUMN movement_date SET NOT NULL;
END $$;
