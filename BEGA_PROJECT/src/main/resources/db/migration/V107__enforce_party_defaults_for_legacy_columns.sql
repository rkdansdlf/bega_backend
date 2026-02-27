DECLARE
  PROCEDURE try_exec(p_sql IN VARCHAR2) IS
  BEGIN
    EXECUTE IMMEDIATE p_sql;
  EXCEPTION
    WHEN OTHERS THEN
      NULL;
  END try_exec;

  PROCEDURE normalize_number_column(
    p_table_name    IN VARCHAR2,
    p_column_name   IN VARCHAR2,
    p_default_value IN NUMBER
  ) IS
    v_exists NUMBER := 0;
    v_nullable USER_TAB_COLS.NULLABLE%TYPE;
    v_default_literal VARCHAR2(32);
  BEGIN
    SELECT COUNT(*)
      INTO v_exists
      FROM user_tab_cols
     WHERE table_name = p_table_name
       AND column_name = p_column_name
       AND data_type IN ('NUMBER', 'FLOAT', 'BINARY_FLOAT', 'BINARY_DOUBLE');

    IF v_exists = 0 THEN
      RETURN;
    END IF;

    v_default_literal := TRIM(TO_CHAR(p_default_value));

    try_exec(
      'UPDATE ' || p_table_name ||
      ' SET ' || p_column_name || ' = NVL(' || p_column_name || ', ' || v_default_literal || ')' ||
      ' WHERE ' || p_column_name || ' IS NULL'
    );

    try_exec(
      'ALTER TABLE ' || p_table_name ||
      ' MODIFY (' || p_column_name || ' DEFAULT ON NULL ' || v_default_literal || ')'
    );
    try_exec(
      'ALTER TABLE ' || p_table_name ||
      ' MODIFY (' || p_column_name || ' DEFAULT ' || v_default_literal || ')'
    );

    BEGIN
      SELECT nullable
        INTO v_nullable
        FROM user_tab_cols
       WHERE table_name = p_table_name
         AND column_name = p_column_name
         AND ROWNUM = 1;
      IF v_nullable = 'Y' THEN
        try_exec(
          'ALTER TABLE ' || p_table_name ||
          ' MODIFY (' || p_column_name || ' NOT NULL)'
        );
      END IF;
    EXCEPTION
      WHEN NO_DATA_FOUND THEN
        NULL;
    END;
  END normalize_number_column;
BEGIN
  normalize_number_column('PARTIES', 'CURRENT_PARTICIPANTS', 1);
  normalize_number_column('PARTIES', 'CURRENTPARTICIPANTS', 1);
  normalize_number_column('PARTY_APPLICATIONS', 'DEPOSIT_AMOUNT', 0);
  normalize_number_column('PARTY_APPLICATIONS', 'DEPOSITAMOUNT', 0);
END;
/
