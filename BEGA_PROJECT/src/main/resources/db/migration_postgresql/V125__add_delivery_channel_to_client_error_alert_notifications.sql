ALTER TABLE client_error_alert_notifications
    ADD COLUMN IF NOT EXISTS delivery_channel VARCHAR(20);

UPDATE client_error_alert_notifications
SET delivery_channel = 'SLACK'
WHERE delivery_channel IS NULL;

ALTER TABLE client_error_alert_notifications
    ALTER COLUMN delivery_channel SET NOT NULL;
