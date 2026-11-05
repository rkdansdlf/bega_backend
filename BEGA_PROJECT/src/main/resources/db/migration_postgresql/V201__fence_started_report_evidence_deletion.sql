ALTER TABLE report_evidence_media_retention ADD COLUMN delete_started_at TIMESTAMP;
ALTER TABLE report_evidence_media_retention ADD COLUMN delete_claim_token VARCHAR(36);

-- A past failure or outstanding claim may still have reached storage; retain a conservative fence.
UPDATE report_evidence_media_retention
   SET delete_started_at = COALESCE(delete_claimed_at, deleted_at, updated_at)
 WHERE delete_claimed_at IS NOT NULL OR attempt_count > 0 OR status = 'DELETED';

CREATE INDEX idx_evidence_owner_started ON report_evidence_media_retention (owner_user_id, delete_started_at);
