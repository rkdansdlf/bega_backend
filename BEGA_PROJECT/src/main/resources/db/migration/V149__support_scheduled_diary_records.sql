DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*)
      INTO v_count
      FROM user_tab_cols
     WHERE table_name = 'BEGA_DIARY'
       AND column_name = 'WINNING';

    IF v_count > 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE bega_diary MODIFY (winning NULL)';
    END IF;
END;
/

BEGIN
    FOR c IN (
        SELECT uc.constraint_name
          FROM user_constraints uc
          JOIN user_cons_columns cc
            ON uc.constraint_name = cc.constraint_name
           AND uc.table_name = cc.table_name
         WHERE uc.table_name = 'BEGA_DIARY'
           AND uc.constraint_type = 'U'
         GROUP BY uc.constraint_name
        HAVING COUNT(*) = 1
           AND MAX(cc.column_name) = 'DIARYDATE'
    ) LOOP
        EXECUTE IMMEDIATE 'ALTER TABLE bega_diary DROP CONSTRAINT ' || c.constraint_name;
    END LOOP;
END;
/

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*)
      INTO v_count
      FROM (
        SELECT uc.constraint_name
          FROM user_constraints uc
          JOIN user_cons_columns cc
            ON uc.constraint_name = cc.constraint_name
           AND uc.table_name = cc.table_name
         WHERE uc.table_name = 'BEGA_DIARY'
           AND uc.constraint_type = 'U'
           AND cc.column_name IN ('USER_ID', 'DIARYDATE')
         GROUP BY uc.constraint_name
        HAVING COUNT(*) = 2
           AND SUM(CASE WHEN cc.column_name = 'USER_ID' THEN 1 ELSE 0 END) = 1
           AND SUM(CASE WHEN cc.column_name = 'DIARYDATE' THEN 1 ELSE 0 END) = 1
      );

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE bega_diary ADD CONSTRAINT uk_bega_diary_user_date UNIQUE (user_id, diarydate)';
    END IF;
END;
/
