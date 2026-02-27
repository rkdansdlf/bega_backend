DECLARE
  PROCEDURE try_exec(p_sql IN VARCHAR2) IS
  BEGIN
    EXECUTE IMMEDIATE p_sql;
  EXCEPTION
    WHEN OTHERS THEN
      NULL;
  END try_exec;

  FUNCTION find_numeric_column(
    p_table_name  IN VARCHAR2,
    p_primary_col IN VARCHAR2,
    p_legacy_col  IN VARCHAR2
  ) RETURN VARCHAR2 IS
    v_column_name USER_TAB_COLS.COLUMN_NAME%TYPE;
  BEGIN
    BEGIN
      SELECT column_name
        INTO v_column_name
        FROM user_tab_cols
       WHERE table_name = p_table_name
         AND column_name = p_primary_col
         AND data_type IN ('NUMBER', 'FLOAT', 'BINARY_FLOAT', 'BINARY_DOUBLE')
         AND ROWNUM = 1;
      RETURN v_column_name;
    EXCEPTION
      WHEN NO_DATA_FOUND THEN
        NULL;
    END;

    BEGIN
      SELECT column_name
        INTO v_column_name
        FROM user_tab_cols
       WHERE table_name = p_table_name
         AND column_name = p_legacy_col
         AND data_type IN ('NUMBER', 'FLOAT', 'BINARY_FLOAT', 'BINARY_DOUBLE')
         AND ROWNUM = 1;
      RETURN v_column_name;
    EXCEPTION
      WHEN NO_DATA_FOUND THEN
        RETURN NULL;
    END;
  END find_numeric_column;

  PROCEDURE normalize_number_column(
    p_table_name    IN VARCHAR2,
    p_primary_col   IN VARCHAR2,
    p_legacy_col    IN VARCHAR2,
    p_default_value IN NUMBER
  ) IS
    v_column_name USER_TAB_COLS.COLUMN_NAME%TYPE;
    v_nullable USER_TAB_COLS.NULLABLE%TYPE;
    v_default_literal VARCHAR2(32);
  BEGIN
    v_column_name := find_numeric_column(p_table_name, p_primary_col, p_legacy_col);
    IF v_column_name IS NULL THEN
      RETURN;
    END IF;

    v_default_literal := TRIM(TO_CHAR(p_default_value));

    try_exec(
      'UPDATE ' || p_table_name ||
      ' SET ' || v_column_name || ' = NVL(' || v_column_name || ', ' || v_default_literal || ')' ||
      ' WHERE ' || v_column_name || ' IS NULL'
    );

    try_exec(
      'ALTER TABLE ' || p_table_name ||
      ' MODIFY (' || v_column_name || ' DEFAULT ON NULL ' || v_default_literal || ')'
    );
    try_exec(
      'ALTER TABLE ' || p_table_name ||
      ' MODIFY (' || v_column_name || ' DEFAULT ' || v_default_literal || ')'
    );

    BEGIN
      SELECT nullable
        INTO v_nullable
        FROM user_tab_cols
       WHERE table_name = p_table_name
         AND column_name = v_column_name
         AND ROWNUM = 1;
      IF v_nullable = 'Y' THEN
        try_exec(
          'ALTER TABLE ' || p_table_name ||
          ' MODIFY (' || v_column_name || ' NOT NULL)'
        );
      END IF;
    EXCEPTION
      WHEN NO_DATA_FOUND THEN
        NULL;
    END;
  END normalize_number_column;
BEGIN
  normalize_number_column(
    p_table_name    => 'PARTIES',
    p_primary_col   => 'CURRENT_PARTICIPANTS',
    p_legacy_col    => 'CURRENTPARTICIPANTS',
    p_default_value => 1
  );

  normalize_number_column(
    p_table_name    => 'PARTY_APPLICATIONS',
    p_primary_col   => 'DEPOSIT_AMOUNT',
    p_legacy_col    => 'DEPOSITAMOUNT',
    p_default_value => 0
  );
END;
/
