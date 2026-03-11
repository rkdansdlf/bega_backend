-- V118: Add structured detail fields for offseason movement collection (Oracle)

DECLARE
    v_table_count NUMBER := 0;
    v_column_count NUMBER := 0;
BEGIN
    SELECT COUNT(*)
      INTO v_table_count
      FROM user_tables
     WHERE table_name = 'PLAYER_MOVEMENTS';

    IF v_table_count = 1 THEN
        SELECT COUNT(*)
          INTO v_column_count
          FROM user_tab_cols
         WHERE table_name = 'PLAYER_MOVEMENTS'
           AND column_name = 'SUMMARY';

        IF v_column_count = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE player_movements ADD (summary VARCHAR2(300 CHAR))';
        END IF;

        SELECT COUNT(*)
          INTO v_column_count
          FROM user_tab_cols
         WHERE table_name = 'PLAYER_MOVEMENTS'
           AND column_name = 'CONTRACT_TERM';

        IF v_column_count = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE player_movements ADD (contract_term VARCHAR2(100 CHAR))';
        END IF;

        SELECT COUNT(*)
          INTO v_column_count
          FROM user_tab_cols
         WHERE table_name = 'PLAYER_MOVEMENTS'
           AND column_name = 'CONTRACT_VALUE';

        IF v_column_count = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE player_movements ADD (contract_value VARCHAR2(120 CHAR))';
        END IF;

        SELECT COUNT(*)
          INTO v_column_count
          FROM user_tab_cols
         WHERE table_name = 'PLAYER_MOVEMENTS'
           AND column_name = 'OPTION_DETAILS';

        IF v_column_count = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE player_movements ADD (option_details VARCHAR2(300 CHAR))';
        END IF;

        SELECT COUNT(*)
          INTO v_column_count
          FROM user_tab_cols
         WHERE table_name = 'PLAYER_MOVEMENTS'
           AND column_name = 'COUNTERPARTY_TEAM';

        IF v_column_count = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE player_movements ADD (counterparty_team VARCHAR2(50 CHAR))';
        END IF;

        SELECT COUNT(*)
          INTO v_column_count
          FROM user_tab_cols
         WHERE table_name = 'PLAYER_MOVEMENTS'
           AND column_name = 'COUNTERPARTY_DETAILS';

        IF v_column_count = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE player_movements ADD (counterparty_details VARCHAR2(500 CHAR))';
        END IF;

        SELECT COUNT(*)
          INTO v_column_count
          FROM user_tab_cols
         WHERE table_name = 'PLAYER_MOVEMENTS'
           AND column_name = 'SOURCE_LABEL';

        IF v_column_count = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE player_movements ADD (source_label VARCHAR2(100 CHAR))';
        END IF;

        SELECT COUNT(*)
          INTO v_column_count
          FROM user_tab_cols
         WHERE table_name = 'PLAYER_MOVEMENTS'
           AND column_name = 'SOURCE_URL';

        IF v_column_count = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE player_movements ADD (source_url VARCHAR2(500 CHAR))';
        END IF;

        SELECT COUNT(*)
          INTO v_column_count
          FROM user_tab_cols
         WHERE table_name = 'PLAYER_MOVEMENTS'
           AND column_name = 'ANNOUNCED_AT';

        IF v_column_count = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE player_movements ADD (announced_at TIMESTAMP NULL)';
        END IF;
    END IF;
END;
/
