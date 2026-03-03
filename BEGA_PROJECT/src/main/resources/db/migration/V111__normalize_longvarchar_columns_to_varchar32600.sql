-- V111: Normalize Oracle LONGVARCHAR-mapped columns to VARCHAR2(32600 CHAR)
-- to satisfy Hibernate validate expectations in prod.

DECLARE
    v_table_count NUMBER := 0;
    v_column_count NUMBER := 0;
    v_data_type VARCHAR2(128);

    PROCEDURE ensure_varchar32600(
        p_table_name IN VARCHAR2,
        p_column_name IN VARCHAR2
    ) IS
        v_tmp_column VARCHAR2(128);
    BEGIN
        SELECT COUNT(*)
          INTO v_table_count
          FROM user_tables
         WHERE table_name = UPPER(p_table_name);

        IF v_table_count = 0 THEN
            RETURN;
        END IF;

        SELECT COUNT(*)
          INTO v_column_count
          FROM user_tab_cols
         WHERE table_name = UPPER(p_table_name)
           AND column_name = UPPER(p_column_name);

        IF v_column_count = 0 THEN
            EXECUTE IMMEDIATE
                'ALTER TABLE ' || p_table_name || ' ADD ' || p_column_name || ' VARCHAR2(32600 CHAR)';
            RETURN;
        END IF;

        SELECT data_type
          INTO v_data_type
          FROM user_tab_cols
         WHERE table_name = UPPER(p_table_name)
           AND column_name = UPPER(p_column_name);

        IF v_data_type = 'VARCHAR2' THEN
            RETURN;
        END IF;

        v_tmp_column := LOWER(p_column_name) || '_v111_tmp';
        EXECUTE IMMEDIATE
            'ALTER TABLE ' || p_table_name || ' ADD ' || v_tmp_column || ' VARCHAR2(32600 CHAR)';

        IF v_data_type = 'CLOB' OR v_data_type = 'NCLOB' THEN
            EXECUTE IMMEDIATE
                'UPDATE ' || p_table_name ||
                ' SET ' || v_tmp_column || ' = DBMS_LOB.SUBSTR(' || p_column_name || ', 32600, 1)';
        ELSE
            EXECUTE IMMEDIATE
                'UPDATE ' || p_table_name ||
                ' SET ' || v_tmp_column || ' = TO_CHAR(' || p_column_name || ')';
        END IF;

        EXECUTE IMMEDIATE
            'ALTER TABLE ' || p_table_name || ' DROP COLUMN ' || p_column_name;
        EXECUTE IMMEDIATE
            'ALTER TABLE ' || p_table_name || ' RENAME COLUMN ' || v_tmp_column || ' TO ' || p_column_name;
    END ensure_varchar32600;
BEGIN
    ensure_varchar32600('cheer_post_reports', 'content');
    ensure_varchar32600('cheer_post_reports', 'description');
    ensure_varchar32600('cheer_post', 'content');
    ensure_varchar32600('cheer_comment', 'content');
    ensure_varchar32600('player_movements', 'details');
    ensure_varchar32600('mate_seller_payout_profiles', 'metadata_json');
    ensure_varchar32600('ranking_predictions', 'prediction_data');
END;
/
