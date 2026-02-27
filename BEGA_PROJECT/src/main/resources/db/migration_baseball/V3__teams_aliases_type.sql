-- V3: teams.aliases 타입 확장
--
-- Oracle V91에 대응: CLOB → VARCHAR2(32600) 변환을
-- PostgreSQL에서는 VARCHAR(255) → TEXT로 적용.
-- 이미 TEXT인 경우 skip (V1 baseline이 TEXT로 생성했으므로 신규 DB는 no-op).

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'teams'
          AND column_name = 'aliases'
          AND data_type = 'character varying'
          AND character_maximum_length IS NOT NULL
          AND character_maximum_length <= 4000
    ) THEN
        ALTER TABLE teams ALTER COLUMN aliases TYPE TEXT;
    END IF;
END;
$$;
