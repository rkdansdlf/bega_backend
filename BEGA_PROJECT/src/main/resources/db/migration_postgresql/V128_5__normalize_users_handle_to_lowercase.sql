-- V128_5: Normalize users.handle values to lowercase canonical form.
-- Fail fast if case-insensitive duplicates already exist so they can be resolved manually.

DO $$
DECLARE
    v_conflict_count INTEGER;
BEGIN
    SELECT COUNT(*)
      INTO v_conflict_count
      FROM (
            SELECT LOWER(BTRIM(handle)) AS normalized_handle
              FROM users
             WHERE handle IS NOT NULL
             GROUP BY LOWER(BTRIM(handle))
            HAVING COUNT(*) > 1
      ) collisions;

    IF v_conflict_count > 0 THEN
        RAISE EXCEPTION 'Cannot apply V128_5: lowercase handle collisions already exist.';
    END IF;
END;
$$;

UPDATE users
   SET handle = LOWER(BTRIM(handle))
 WHERE handle IS NOT NULL
   AND handle <> LOWER(BTRIM(handle));
