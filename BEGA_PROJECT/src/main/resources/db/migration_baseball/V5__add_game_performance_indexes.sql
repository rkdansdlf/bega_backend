-- V5: 운영 baseball PostgreSQL 누락 복합 인덱스 + UPPER(game_status) 함수 인덱스 추가
DO $$
BEGIN
    IF to_regclass('public.game') IS NULL THEN RETURN; END IF;

    -- V67 포트: is_dummy 필터링이 있는 경기 범위 복합 인덱스
    CREATE INDEX IF NOT EXISTS idx_game_range_filter
        ON game (game_date, is_dummy, game_status, home_team, away_team, game_id);

    CREATE INDEX IF NOT EXISTS idx_game_status_dummy_date
        ON game (game_status, is_dummy, game_date);

    -- V157 포트: 정규팀 활성 경기 조회용 partial index
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
         WHERE schemaname = 'public' AND tablename = 'game'
           AND indexname = 'idx_game_range_canonical_active'
    ) THEN
        CREATE INDEX idx_game_range_canonical_active
            ON public.game (game_date, home_team, away_team, game_id)
            WHERE is_dummy IS NOT TRUE AND game_id NOT LIKE 'MOCK%';
    END IF;

    -- UPPER(game_status) 함수 인덱스
    -- HOME_SCHEDULED_WINDOW_PROJECTION_QUERY 및 navigation 쿼리의 UPPER() 호출과 일치
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
