-- V2: team_franchises.metadata_json 타입 확장
--
-- Oracle V90에 대응: CLOB → VARCHAR2(32600) 변환을
-- PostgreSQL에서는 VARCHAR(255) → TEXT로 적용.
-- 이미 TEXT인 경우 skip (V1 baseline이 TEXT로 생성했으므로 신규 DB는 no-op).

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'team_franchises'
          AND column_name = 'metadata_json'
          AND data_type = 'character varying'
          AND character_maximum_length IS NOT NULL
          AND character_maximum_length <= 4000
    ) THEN
        ALTER TABLE team_franchises ALTER COLUMN metadata_json TYPE TEXT;
    END IF;
END;
$$;
