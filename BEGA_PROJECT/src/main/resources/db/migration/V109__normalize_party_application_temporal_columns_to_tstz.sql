DECLARE
  PROCEDURE ensure_tstz(
    p_table_name  IN VARCHAR2,
    p_column_name IN VARCHAR2
  ) IS
    v_data_type USER_TAB_COLS.DATA_TYPE%TYPE;
  BEGIN
    BEGIN
      SELECT data_type
        INTO v_data_type
        FROM user_tab_cols
       WHERE table_name = p_table_name
         AND column_name = p_column_name
         AND ROWNUM = 1;
    EXCEPTION
      WHEN NO_DATA_FOUND THEN
        RETURN;
    END;

    IF v_data_type = 'TIMESTAMP WITH TIME ZONE' THEN
      RETURN;
    END IF;

    BEGIN
      EXECUTE IMMEDIATE
        'ALTER TABLE ' || p_table_name ||
        ' MODIFY (' || p_column_name || ' TIMESTAMP(6) WITH TIME ZONE)';
    EXCEPTION
      WHEN OTHERS THEN
        NULL;
    END;
  END ensure_tstz;
BEGIN
  ensure_tstz('PARTY_APPLICATIONS', 'APPROVED_AT');
  ensure_tstz('PARTY_APPLICATIONS', 'REJECTED_AT');
  ensure_tstz('PARTY_APPLICATIONS', 'RESPONSE_DEADLINE');
END;
/
