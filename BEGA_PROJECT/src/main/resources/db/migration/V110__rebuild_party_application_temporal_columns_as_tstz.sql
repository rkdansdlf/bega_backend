DECLARE
  FUNCTION column_exists(
    p_table_name  IN VARCHAR2,
    p_column_name IN VARCHAR2
  ) RETURN BOOLEAN IS
    v_count NUMBER;
  BEGIN
    SELECT COUNT(*)
      INTO v_count
      FROM user_tab_cols
     WHERE table_name = p_table_name
       AND column_name = p_column_name;
    RETURN v_count > 0;
  END column_exists;

  FUNCTION column_type(
    p_table_name  IN VARCHAR2,
    p_column_name IN VARCHAR2
  ) RETURN VARCHAR2 IS
    v_data_type USER_TAB_COLS.DATA_TYPE%TYPE;
  BEGIN
    SELECT data_type
      INTO v_data_type
      FROM user_tab_cols
     WHERE table_name = p_table_name
       AND column_name = p_column_name
       AND ROWNUM = 1;
    RETURN v_data_type;
  EXCEPTION
    WHEN NO_DATA_FOUND THEN
      RETURN NULL;
  END column_type;

  PROCEDURE rebuild_tstz_column(
    p_table_name  IN VARCHAR2,
    p_column_name IN VARCHAR2
  ) IS
    v_data_type VARCHAR2(128);
    v_tmp_column VARCHAR2(128);
  BEGIN
    IF NOT column_exists(p_table_name, p_column_name) THEN
      RETURN;
    END IF;

    v_data_type := column_type(p_table_name, p_column_name);
    IF v_data_type = 'TIMESTAMP WITH TIME ZONE' THEN
      RETURN;
    END IF;

    v_tmp_column := p_column_name || '_TZTMP';
    IF LENGTH(v_tmp_column) > 30 THEN
      v_tmp_column := SUBSTR(p_column_name, 1, 23) || '_TZTMP';
    END IF;

    IF column_exists(p_table_name, v_tmp_column) THEN
      EXECUTE IMMEDIATE 'ALTER TABLE ' || p_table_name || ' DROP COLUMN ' || v_tmp_column;
    END IF;

    EXECUTE IMMEDIATE
      'ALTER TABLE ' || p_table_name ||
      ' ADD (' || v_tmp_column || ' TIMESTAMP(6) WITH TIME ZONE)';

    EXECUTE IMMEDIATE
      'UPDATE ' || p_table_name ||
      ' SET ' || v_tmp_column ||
      ' = CASE WHEN ' || p_column_name || ' IS NULL THEN NULL ' ||
      'ELSE FROM_TZ(CAST(' || p_column_name || ' AS TIMESTAMP), SESSIONTIMEZONE) END';

    EXECUTE IMMEDIATE
      'ALTER TABLE ' || p_table_name || ' DROP COLUMN ' || p_column_name;

    EXECUTE IMMEDIATE
      'ALTER TABLE ' || p_table_name || ' RENAME COLUMN ' || v_tmp_column || ' TO ' || p_column_name;
  END rebuild_tstz_column;
BEGIN
  rebuild_tstz_column('PARTY_APPLICATIONS', 'APPROVED_AT');
  rebuild_tstz_column('PARTY_APPLICATIONS', 'REJECTED_AT');
  rebuild_tstz_column('PARTY_APPLICATIONS', 'RESPONSE_DEADLINE');
END;
/
