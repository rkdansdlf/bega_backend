-- V148: Add indexes for mypage mate history lookups (Oracle).

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*)
      INTO v_count
      FROM user_indexes
     WHERE table_name = 'PARTIES'
       AND index_name = 'IDX_PARTIES_HOST_CREATED';

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX idx_parties_host_created ON parties(hostid, createdat DESC, id DESC)';
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
       AND index_name = 'IDX_PARTY_APP_APPLICANT_PARTY';

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX idx_party_app_applicant_party ON party_applications(applicantid, is_approved, partyid)';
    END IF;
END;
/
