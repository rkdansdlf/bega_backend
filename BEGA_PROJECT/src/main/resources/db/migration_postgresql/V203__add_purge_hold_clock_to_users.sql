-- Purge-hold clock for deleted accounts: when a purge hold was first seen, and the day of the last alert.
-- Stored on the account row so a restart does not reset the 24-hour alert threshold.
ALTER TABLE users ADD COLUMN IF NOT EXISTS purge_hold_started_at TIMESTAMP NULL;
ALTER TABLE users ADD COLUMN IF NOT EXISTS purge_hold_alerted_on DATE NULL;
