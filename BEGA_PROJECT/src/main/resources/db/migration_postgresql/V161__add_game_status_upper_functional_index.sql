-- V161: UPPER(game_status) 함수 인덱스 추가 (migration_baseball/V5 dev parity)
DO $$
BEGIN
    IF to_regclass('public.game') IS NULL THEN RETURN; END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
         WHERE schemaname = 'public' AND tablename = 'game'
           AND indexname = 'idx_game_status_upper_date'
    ) THEN
        CREATE INDEX idx_game_status_upper_date
            ON game (UPPER(game_status), game_date)
            WHERE is_dummy IS NOT TRUE AND game_id NOT LIKE 'MOCK%';
    END IF;
END $$;
