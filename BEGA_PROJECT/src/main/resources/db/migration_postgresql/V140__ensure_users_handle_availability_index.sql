-- V140: Ensure handle availability checks use an indexed unique lookup on PostgreSQL.
-- V52 creates uq_users_handle for fresh dev DBs; this backstop covers repaired or legacy schemas.

DO $$
DECLARE
    v_collision_count INTEGER;
    v_has_unique_handle_index BOOLEAN;
    v_has_conflicting_index_name BOOLEAN;
BEGIN
    SELECT COUNT(*)
      INTO v_collision_count
      FROM (
            SELECT LOWER(BTRIM(handle)) AS normalized_handle
              FROM users
             WHERE handle IS NOT NULL
             GROUP BY LOWER(BTRIM(handle))
            HAVING COUNT(*) > 1
      ) collisions;

    IF v_collision_count > 0 THEN
        RAISE EXCEPTION 'Cannot apply V140: duplicate users.handle values exist after lowercase normalization.';
    END IF;

    SELECT EXISTS (
            SELECT 1
              FROM pg_index ix
              JOIN pg_class table_class
                ON table_class.oid = ix.indrelid
              JOIN pg_class index_class
                ON index_class.oid = ix.indexrelid
              JOIN pg_namespace namespace
                ON namespace.oid = table_class.relnamespace
             WHERE namespace.nspname = current_schema()
               AND table_class.relname = 'users'
               AND ix.indisunique
               AND ix.indnkeyatts = 1
               AND (
                    SELECT att.attname
                      FROM pg_attribute att
                     WHERE att.attrelid = table_class.oid
                       AND att.attnum = ix.indkey[0]
               ) = 'handle'
        )
      INTO v_has_unique_handle_index;

    IF NOT v_has_unique_handle_index THEN
        SELECT EXISTS (
                SELECT 1
                  FROM pg_class index_class
                  JOIN pg_namespace namespace
                    ON namespace.oid = index_class.relnamespace
                 WHERE namespace.nspname = current_schema()
                   AND index_class.relkind = 'i'
                   AND index_class.relname = 'uq_users_handle'
            )
          INTO v_has_conflicting_index_name;

        IF v_has_conflicting_index_name THEN
            RAISE EXCEPTION 'Cannot apply V140: uq_users_handle exists but is not a unique users(handle) index.';
        END IF;

        EXECUTE 'CREATE UNIQUE INDEX uq_users_handle ON users (handle)';
    END IF;
END;
$$;
