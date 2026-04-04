-- V128: Normalize users.handle values to lowercase canonical form.
-- Fail fast if case-insensitive duplicates already exist so they can be resolved manually.

DECLARE
    v_conflict_count NUMBER;
BEGIN
    SELECT COUNT(*)
      INTO v_conflict_count
      FROM (
            SELECT LOWER(TRIM(handle)) AS normalized_handle
              FROM users
             WHERE handle IS NOT NULL
             GROUP BY LOWER(TRIM(handle))
            HAVING COUNT(*) > 1
      );

    IF v_conflict_count > 0 THEN
        RAISE_APPLICATION_ERROR(-20003, 'Cannot apply V128: lowercase handle collisions already exist.');
    END IF;
END;
/

UPDATE users
   SET handle = LOWER(TRIM(handle))
 WHERE handle IS NOT NULL
   AND handle != LOWER(TRIM(handle));
