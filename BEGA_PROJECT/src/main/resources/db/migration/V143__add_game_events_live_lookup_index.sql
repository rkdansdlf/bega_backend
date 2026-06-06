-- V143: Speed up live summary latest-event lookups on Oracle.

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*)
      INTO v_count
      FROM user_indexes
     WHERE table_name = 'GAME_EVENTS'
       AND index_name = 'IDX_GAME_EVENTS_LIVE_LOOKUP';

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX IDX_GAME_EVENTS_LIVE_LOOKUP ON game_events(game_id, event_seq)';
    END IF;
END;
/
