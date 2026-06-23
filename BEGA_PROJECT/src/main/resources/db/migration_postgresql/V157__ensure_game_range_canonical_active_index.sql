-- V157: Repair missing canonical /matches/range lookup index (PostgreSQL).

DO $$
BEGIN
    IF to_regclass('public.game') IS NULL THEN
        RETURN;
    END IF;

    IF NOT EXISTS (
        SELECT 1
          FROM pg_indexes
         WHERE schemaname = 'public'
           AND tablename = 'game'
           AND indexname = 'idx_game_range_canonical_active'
    ) THEN
        CREATE INDEX idx_game_range_canonical_active
            ON public.game (game_date, home_team, away_team, game_id)
            WHERE is_dummy IS NOT TRUE
              AND game_id NOT LIKE 'MOCK%';
    END IF;
END $$;
