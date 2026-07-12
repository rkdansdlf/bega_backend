-- V169: Add composite indexes for client error alert evaluation on PostgreSQL.

CREATE INDEX IF NOT EXISTS idx_client_error_events_alert_window
    ON client_error_events (occurred_at DESC, fingerprint, bucket);

CREATE INDEX IF NOT EXISTS idx_client_error_events_fp_occurred
    ON client_error_events (fingerprint, occurred_at DESC);

CREATE INDEX IF NOT EXISTS idx_client_error_alert_notif_cooldown
    ON client_error_alert_notifications (fingerprint, notified_at DESC);
