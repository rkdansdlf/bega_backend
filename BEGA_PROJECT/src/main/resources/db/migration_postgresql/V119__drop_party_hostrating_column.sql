-- V119: Drop legacy parties.hostrating after migrating public APIs to review summary fields.

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
          FROM information_schema.columns
         WHERE table_schema = 'public'
           AND table_name = 'parties'
           AND column_name = 'hostrating'
    ) THEN
        EXECUTE 'ALTER TABLE parties DROP COLUMN hostrating';
    END IF;
END;
$$;
