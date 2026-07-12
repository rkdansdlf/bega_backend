-- V163: Add team/id index for Cheer post change polling on Oracle.

DECLARE
    v_table_count NUMBER;
    v_index_count NUMBER;
BEGIN
    SELECT COUNT(*)
      INTO v_table_count
      FROM user_tables
     WHERE table_name = 'CHEER_POST';

    IF v_table_count > 0 THEN
        SELECT COUNT(*)
          INTO v_index_count
          FROM user_indexes
         WHERE table_name = 'CHEER_POST'
           AND index_name = 'IDX_CHEER_POST_TEAM_ID_DESC';

        IF v_index_count = 0 THEN
            EXECUTE IMMEDIATE 'CREATE INDEX idx_cheer_post_team_id_desc ON cheer_post(team_id, id DESC)';
        END IF;
    END IF;
END;
/
