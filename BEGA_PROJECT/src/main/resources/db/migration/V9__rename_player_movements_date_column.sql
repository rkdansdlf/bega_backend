-- Rename 'DATE' column to 'movement_date' for Oracle compatibility
-- Using PL/SQL to sensitively handle uppercase/lowercase and avoid ORA-00904 if already renamed
DECLARE
  v_col_name VARCHAR2(32);
BEGIN
  -- Try to find the column name 'date' or 'DATE' in PLAYER_MOVEMENTS
  BEGIN
    SELECT column_name INTO v_col_name
    FROM user_tab_columns
    WHERE table_name = UPPER('player_movements')
      AND (column_name = 'DATE' OR column_name = 'date');
    
    EXECUTE IMMEDIATE 'ALTER TABLE player_movements RENAME COLUMN "' || v_col_name || '" TO movement_date';
  EXCEPTION
    WHEN NO_DATA_FOUND THEN
      NULL; -- Column not found, could be already renamed or missing
  END;
END;
/
