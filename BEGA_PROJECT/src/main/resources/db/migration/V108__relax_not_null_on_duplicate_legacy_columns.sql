DECLARE
  PROCEDURE relax_not_null_for_legacy_pairs(p_table_name IN VARCHAR2) IS
  BEGIN
    FOR rec IN (
      SELECT c.column_name
        FROM user_tab_cols c
       WHERE c.table_name = p_table_name
         AND c.nullable = 'N'
         AND INSTR(c.column_name, '_') > 0
         AND EXISTS (
               SELECT 1
                 FROM user_tab_cols legacy_col
                WHERE legacy_col.table_name = c.table_name
                  AND legacy_col.column_name = REPLACE(c.column_name, '_', '')
             )
    ) LOOP
      BEGIN
        EXECUTE IMMEDIATE
          'ALTER TABLE ' || p_table_name || ' MODIFY (' || rec.column_name || ' NULL)';
      EXCEPTION
        WHEN OTHERS THEN
          NULL;
      END;
    END LOOP;
  END relax_not_null_for_legacy_pairs;
BEGIN
  relax_not_null_for_legacy_pairs('PARTIES');
  relax_not_null_for_legacy_pairs('PARTY_APPLICATIONS');
END;
/
