-- V149: Add indexes for mate list, party application state, and diary lookups on PostgreSQL.

CREATE INDEX IF NOT EXISTS idx_parties_status_created
    ON parties (status, createdat DESC);

CREATE INDEX IF NOT EXISTS idx_parties_team_status_created
    ON parties (teamid, status, createdat DESC);

CREATE INDEX IF NOT EXISTS idx_parties_date_status_created
    ON parties (gamedate, status, createdat DESC);

CREATE INDEX IF NOT EXISTS idx_party_app_party_state
    ON party_applications (partyid, is_approved, is_rejected);

CREATE INDEX IF NOT EXISTS idx_party_app_applicant_state
    ON party_applications (applicantid, is_approved, is_rejected);

CREATE INDEX IF NOT EXISTS idx_party_app_deadline_state
    ON party_applications (is_approved, is_rejected, response_deadline);

CREATE INDEX IF NOT EXISTS idx_bega_diary_user_date
    ON bega_diary (user_id, diarydate DESC);

CREATE INDEX IF NOT EXISTS idx_bega_diary_seat_view
    ON bega_diary (stadium, section, type, diarydate DESC);
