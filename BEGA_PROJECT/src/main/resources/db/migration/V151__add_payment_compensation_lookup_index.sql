-- V151: Optimize payment compensation reconciliation lookup path (Oracle).

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*)
      INTO v_count
      FROM user_tables
     WHERE table_name = 'PAYMENT_INTENTS';

    IF v_count > 0 THEN
        SELECT COUNT(*)
          INTO v_count
          FROM user_ind_columns
         WHERE table_name = 'PAYMENT_INTENTS'
           AND index_name = 'IDX_PAYMENT_INTENTS_STATUS_UPDATED_AT'
           AND (
                (column_name = 'STATUS' AND column_position = 1)
             OR (column_name = 'UPDATED_AT' AND column_position = 2)
           );

        IF v_count < 2 THEN
            EXECUTE IMMEDIATE
                'CREATE INDEX idx_payment_intents_status_updated_at
                    ON payment_intents(status, updated_at)';
        END IF;
    END IF;
END;
/
