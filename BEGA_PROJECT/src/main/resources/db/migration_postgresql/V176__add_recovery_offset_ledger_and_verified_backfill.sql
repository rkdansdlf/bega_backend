-- Add auditable recovery-offset reservations and quarantine unverified legacy payout completions.

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
          FROM payout_transactions
         GROUP BY payment_transaction_id
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'MATE_PAYOUT_DUPLICATES_REQUIRE_MANUAL_RECONCILIATION';
    END IF;

    IF EXISTS (
        SELECT 1
          FROM payout_transactions payout
         WHERE payout.status = 'COMPLETED'
           AND COALESCE(payout.failure_code, '') <> 'PAYOUT_COMPLETION_VERIFIED'
    ) THEN
        RAISE EXCEPTION 'MATE_LEGACY_PAYOUT_COMPLETION_REQUIRES_MANUAL_RECONCILIATION';
    END IF;

    IF EXISTS (
        SELECT 1
          FROM payout_transactions
         WHERE COALESCE(recovery_offset_amount, 0) <> 0
    ) THEN
        RAISE EXCEPTION 'MATE_LEGACY_RECOVERY_OFFSET_REQUIRES_MANUAL_RECONCILIATION';
    END IF;

    IF EXISTS (
        SELECT 1
         FROM payout_transactions
         WHERE status = 'REQUESTED'
            OR (status = 'FAILED' AND next_retry_at IS NOT NULL)
            OR (status = 'FAILED' AND failure_code IN (
                'TOSS_PAYOUT_REQUEST_FAILED',
                'TOSS_PAYOUT_EMPTY_RESPONSE',
                'TOSS_PAYOUT_NO_PROVIDER_REF',
                'PAYOUT_MANUAL_RECONCILIATION_REQUIRED'
            ))
    ) THEN
        RAISE EXCEPTION 'MATE_ACTIVE_PAYOUT_REQUIRES_MANUAL_RECONCILIATION';
    END IF;
END
$$;

ALTER TABLE IF EXISTS payout_transactions
    ADD COLUMN IF NOT EXISTS provider_code VARCHAR(30),
    ADD COLUMN IF NOT EXISTS provider_seller_id VARCHAR(200),
    ADD COLUMN IF NOT EXISTS claim_protocol VARCHAR(30);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_payout_offset_protocol'
    ) THEN
        ALTER TABLE payout_transactions
            ADD CONSTRAINT ck_payout_offset_protocol
            CHECK (COALESCE(recovery_offset_amount, 0) = 0
                OR COALESCE(claim_protocol, '') = 'SNAPSHOT_V1');
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_payout_requested_protocol'
    ) THEN
        ALTER TABLE payout_transactions
            ADD CONSTRAINT ck_payout_requested_protocol
            CHECK (status <> 'REQUESTED'
                OR COALESCE(claim_protocol, '') = 'SNAPSHOT_V1');
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_payout_completion_verified'
    ) THEN
        ALTER TABLE payout_transactions
            ADD CONSTRAINT ck_payout_completion_verified
            CHECK (status <> 'COMPLETED'
                OR COALESCE(failure_code, '') = 'PAYOUT_COMPLETION_VERIFIED');
    END IF;
END
$$;

CREATE TABLE IF NOT EXISTS seller_recovery_offset_allocations (
    id BIGSERIAL PRIMARY KEY,
    payout_transaction_id BIGINT NOT NULL,
    recovery_id BIGINT NOT NULL,
    amount INTEGER NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_sroa_payout_recovery
    ON seller_recovery_offset_allocations(payout_transaction_id, recovery_id);

CREATE INDEX IF NOT EXISTS idx_sroa_recovery_status
    ON seller_recovery_offset_allocations(recovery_id, status);

CREATE INDEX IF NOT EXISTS idx_sroa_payout_status
    ON seller_recovery_offset_allocations(payout_transaction_id, status, id);

CREATE INDEX IF NOT EXISTS idx_payout_status_next_retry
    ON payout_transactions(status, next_retry_at, id);

INSERT INTO seller_payout_recoveries (
    source_payment_transaction_id,
    payout_transaction_id,
    seller_user_id,
    original_paid_amount,
    target_net_amount,
    recovery_amount,
    recovered_amount,
    status,
    created_at,
    updated_at
)
SELECT
    payment.id,
    payout.id,
    payment.seller_user_id,
    GREATEST(
        COALESCE(payout.requested_amount, 0)
            + COALESCE(payout.recovery_offset_amount, 0),
        0),
    GREATEST(COALESCE(payment.net_amount, 0), 0),
    GREATEST(
        COALESCE(payout.requested_amount, 0)
            + COALESCE(payout.recovery_offset_amount, 0)
            - COALESCE(payment.net_amount, 0),
        0),
    0,
    CASE
        WHEN COALESCE(payout.requested_amount, 0)
                + COALESCE(payout.recovery_offset_amount, 0)
                - COALESCE(payment.net_amount, 0) > 0
            THEN 'PENDING'
        ELSE 'RECOVERED'
    END,
    NOW(),
    NOW()
FROM payment_transactions payment
JOIN payout_transactions payout
  ON payout.payment_transaction_id = payment.id
 AND payout.status = 'COMPLETED'
 AND payout.failure_code = 'PAYOUT_COMPLETION_VERIFIED'
WHERE payment.settlement_status = 'REFUNDED_AFTER_SETTLEMENT'
ON CONFLICT (source_payment_transaction_id) DO NOTHING;
