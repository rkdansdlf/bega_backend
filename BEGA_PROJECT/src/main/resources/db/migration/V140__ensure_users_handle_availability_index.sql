-- V140: Ensure handle availability checks use an indexed unique lookup on Oracle.
-- Fail fast when existing data would make the application-level lowercase handle contract unsafe.

DECLARE
    v_collision_count NUMBER;
    v_unique_index_count NUMBER;
    v_conflicting_name_count NUMBER;
BEGIN
    SELECT COUNT(*)
      INTO v_collision_count
      FROM (
            SELECT LOWER(TRIM(handle)) AS normalized_handle
              FROM users
             WHERE handle IS NOT NULL
             GROUP BY LOWER(TRIM(handle))
            HAVING COUNT(*) > 1
      );

    IF v_collision_count > 0 THEN
        RAISE_APPLICATION_ERROR(-20007, 'Cannot apply V140: duplicate users.handle values exist after lowercase normalization.');
    END IF;

    SELECT COUNT(*)
      INTO v_unique_index_count
      FROM (
            SELECT i.index_name
              FROM user_indexes i
              JOIN user_ind_columns c
                ON c.index_name = i.index_name
               AND c.table_name = i.table_name
             WHERE i.table_name = 'USERS'
               AND i.uniqueness = 'UNIQUE'
             GROUP BY i.index_name
            HAVING COUNT(*) = 1
               AND MAX(CASE WHEN c.column_name = 'HANDLE' THEN 1 ELSE 0 END) = 1
      );

    IF v_unique_index_count = 0 THEN
        SELECT COUNT(*)
          INTO v_conflicting_name_count
          FROM user_objects
         WHERE object_name = 'UQ_USERS_HANDLE';

        IF v_conflicting_name_count > 0 THEN
            RAISE_APPLICATION_ERROR(-20008, 'Cannot apply V140: UQ_USERS_HANDLE exists but is not a unique users(handle) index.');
        END IF;

        EXECUTE IMMEDIATE 'CREATE UNIQUE INDEX uq_users_handle ON users (handle)';
    END IF;
END;
/
