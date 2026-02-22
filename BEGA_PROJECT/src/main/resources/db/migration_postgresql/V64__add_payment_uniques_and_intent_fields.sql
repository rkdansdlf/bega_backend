-- V64: Add payment_intents flow fields and party_applications unique indexes (PostgreSQL)

ALTER TABLE payment_intents
    ADD COLUMN IF NOT EXISTS flow_type VARCHAR(30),
    ADD COLUMN IF NOT EXISTS cancel_policy_version VARCHAR(50);

UPDATE payment_intents
SET flow_type = CASE
    WHEN payment_type = 'FULL' THEN 'SELLING_FULL'
    ELSE 'DEPOSIT'
END
WHERE flow_type IS NULL;

ALTER TABLE payment_intents
    ALTER COLUMN flow_type SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_party_applications_order_id
    ON party_applications(order_id)
    WHERE order_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_party_applications_payment_key
    ON party_applications(payment_key)
    WHERE payment_key IS NOT NULL;
