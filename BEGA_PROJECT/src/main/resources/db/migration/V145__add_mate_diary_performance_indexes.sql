-- V145: Add indexes for mate list, party application state, and diary lookups on Oracle.

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*)
      INTO v_count
      FROM user_indexes
     WHERE table_name = 'PARTIES'
       AND index_name = 'IDX_PARTIES_STATUS_CREATED';

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX idx_parties_status_created ON parties(status, createdat DESC)';
    END IF;
END;
/

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*)
      INTO v_count
      FROM user_indexes
     WHERE table_name = 'PARTIES'
       AND index_name = 'IDX_PARTIES_TEAM_STATUS_CREATED';

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX idx_parties_team_status_created ON parties(teamid, status, createdat DESC)';
    END IF;
END;
/

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*)
      INTO v_count
      FROM user_indexes
     WHERE table_name = 'PARTIES'
       AND index_name = 'IDX_PARTIES_DATE_STATUS_CREATED';

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX idx_parties_date_status_created ON parties(gamedate, status, createdat DESC)';
    END IF;
END;
/

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*)
      INTO v_count
      FROM user_indexes
     WHERE table_name = 'PARTY_APPLICATIONS'
       AND index_name = 'IDX_PARTY_APP_PARTY_STATE';

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX idx_party_app_party_state ON party_applications(partyid, is_approved, is_rejected)';
    END IF;
END;
/

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*)
      INTO v_count
      FROM user_indexes
     WHERE table_name = 'PARTY_APPLICATIONS'
       AND index_name = 'IDX_PARTY_APP_APPLICANT_STATE';

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX idx_party_app_applicant_state ON party_applications(applicantid, is_approved, is_rejected)';
    END IF;
END;
/

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*)
      INTO v_count
      FROM user_indexes
     WHERE table_name = 'PARTY_APPLICATIONS'
       AND index_name = 'IDX_PARTY_APP_DEADLINE_STATE';

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX idx_party_app_deadline_state ON party_applications(is_approved, is_rejected, response_deadline)';
    END IF;
END;
/

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*)
      INTO v_count
      FROM user_indexes
     WHERE table_name = 'BEGA_DIARY'
       AND index_name = 'IDX_BEGA_DIARY_USER_DATE';

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX idx_bega_diary_user_date ON bega_diary(user_id, diarydate DESC)';
    END IF;
END;
/

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*)
      INTO v_count
      FROM user_indexes
     WHERE table_name = 'BEGA_DIARY'
       AND index_name = 'IDX_BEGA_DIARY_SEAT_VIEW';

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX idx_bega_diary_seat_view ON bega_diary(stadium, section, type, diarydate DESC)';
    END IF;
END;
/
