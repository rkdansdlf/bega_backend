-- V143: Speed up live summary latest-event lookups on Oracle.

DECLARE
    v_table_count NUMBER;
    v_index_count NUMBER;
BEGIN
    SELECT COUNT(*)
      INTO v_table_count
      FROM user_tables
     WHERE table_name = 'GAME_EVENTS';

    IF v_table_count > 0 THEN
        SELECT COUNT(*)
          INTO v_index_count
          FROM user_indexes
         WHERE table_name = 'GAME_EVENTS'
           AND index_name = 'IDX_GAME_EVENTS_LIVE_LOOKUP';

        IF v_index_count = 0 THEN
            EXECUTE IMMEDIATE 'CREATE INDEX IDX_GAME_EVENTS_LIVE_LOOKUP ON game_events(game_id, event_seq)';
        END IF;
    END IF;
END;
/
