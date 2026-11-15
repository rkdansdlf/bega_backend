-- Stage-1 column for the account content anonymization retry scheduler (AccountSecurityScheduler).
-- Must sort before V193 and be applied by a target-192 first stage, because UserEntity maps this
-- column and production runs ddl-auto: validate. Kept out of V194 (second stage) for that reason.
ALTER TABLE users ADD COLUMN IF NOT EXISTS content_anonymization_retry_at TIMESTAMP;
CREATE INDEX IF NOT EXISTS idx_user_content_anon_retry ON users (content_anonymization_retry_at, id);
