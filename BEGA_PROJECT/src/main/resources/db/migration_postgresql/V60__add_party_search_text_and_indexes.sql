-- V60: parties 검색 최적화를 위한 search_text 컬럼 및 인덱스 추가 (PostgreSQL)

ALTER TABLE IF EXISTS parties
    ADD COLUMN IF NOT EXISTS search_text VARCHAR(2000);

DO $$
BEGIN
    IF to_regclass('public.parties') IS NULL THEN
        RETURN;
    END IF;

    UPDATE parties
    SET search_text = lower(
        trim(
            concat_ws(
                ' ',
                coalesce(stadium, ''),
                coalesce(hometeam, ''),
                coalesce(awayteam, ''),
                coalesce(section, ''),
                coalesce(hostname, ''),
                coalesce(description, '')
            )
        )
    )
    WHERE search_text IS NULL;

    CREATE INDEX IF NOT EXISTS idx_parties_search_text ON parties (search_text);
    CREATE INDEX IF NOT EXISTS idx_parties_status_gamedate ON parties (status, gamedate DESC);
END;
$$;
