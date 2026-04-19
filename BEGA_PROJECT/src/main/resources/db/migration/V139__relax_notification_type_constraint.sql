DECLARE
    v_column_count NUMBER := 0;
BEGIN
    SELECT COUNT(*)
      INTO v_column_count
      FROM user_tab_columns
     WHERE table_name = 'NOTIFICATIONS'
       AND column_name = 'TYPE';

    IF v_column_count > 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE notifications MODIFY ("TYPE" VARCHAR2(50 CHAR))';
    END IF;
END;
/

DECLARE
BEGIN
    FOR constraint_row IN (
        SELECT DISTINCT c.constraint_name
          FROM user_constraints c
          JOIN user_cons_columns cc
            ON cc.constraint_name = c.constraint_name
         WHERE c.table_name = 'NOTIFICATIONS'
           AND c.constraint_type = 'C'
           AND cc.column_name = 'TYPE'
           AND UPPER(NVL(c.search_condition_vc, '')) NOT LIKE '%IS NOT NULL%'
    ) LOOP
        EXECUTE IMMEDIATE 'ALTER TABLE notifications DROP CONSTRAINT ' || constraint_row.constraint_name;
    END LOOP;
END;
/
