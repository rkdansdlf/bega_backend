-- V153: Add indexes for mypage mate history lookups (PostgreSQL).

CREATE INDEX IF NOT EXISTS idx_parties_host_created
    ON parties (hostid, createdat DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_party_app_applicant_party
    ON party_applications (applicantid, is_approved, partyid);
