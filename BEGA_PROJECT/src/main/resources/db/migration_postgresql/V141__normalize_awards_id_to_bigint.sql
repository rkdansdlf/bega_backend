-- Normalize legacy awards.id SERIAL columns to match AwardEntity.id Long mapping.

DO $$
DECLARE
    awards_id_sequence text;
BEGIN
    IF to_regclass('public.awards') IS NOT NULL THEN
        IF EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = 'awards'
              AND column_name = 'id'
              AND data_type IN ('integer', 'smallint')
        ) THEN
            ALTER TABLE public.awards
            ALTER COLUMN id TYPE BIGINT USING id::bigint;
        END IF;

        awards_id_sequence := pg_get_serial_sequence('public.awards', 'id');
        IF awards_id_sequence IS NOT NULL THEN
            EXECUTE format('ALTER SEQUENCE %s AS BIGINT', awards_id_sequence::regclass);
        END IF;
    END IF;
END;
$$;
