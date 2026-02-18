-- Align PostgreSQL schema with current JPA mappings for Mate ticket/application features.

-- parties: reservation number is expected by the entity.
ALTER TABLE parties
    ADD COLUMN IF NOT EXISTS reservation_number VARCHAR(50);

-- party_applications: snake_case columns expected by PartyApplication entity.
ALTER TABLE party_applications
    ADD COLUMN IF NOT EXISTS applicant_name VARCHAR(50),
    ADD COLUMN IF NOT EXISTS applicant_badge VARCHAR(20),
    ADD COLUMN IF NOT EXISTS applicant_rating DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS deposit_amount INTEGER,
    ADD COLUMN IF NOT EXISTS is_paid BOOLEAN,
    ADD COLUMN IF NOT EXISTS is_approved BOOLEAN,
    ADD COLUMN IF NOT EXISTS is_rejected BOOLEAN,
    ADD COLUMN IF NOT EXISTS payment_type VARCHAR(20),
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS approved_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS rejected_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS response_deadline TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS ticket_verified BOOLEAN,
    ADD COLUMN IF NOT EXISTS ticket_image_url VARCHAR(500);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'party_applications'
          AND column_name = 'applicantname'
    ) THEN
        EXECUTE '
            UPDATE party_applications
            SET applicant_name = COALESCE(applicant_name, applicantname)
            WHERE applicant_name IS NULL
        ';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'party_applications'
          AND column_name = 'applicantbadge'
    ) THEN
        EXECUTE '
            UPDATE party_applications
            SET applicant_badge = COALESCE(applicant_badge, applicantbadge)
            WHERE applicant_badge IS NULL
        ';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'party_applications'
          AND column_name = 'applicantrating'
    ) THEN
        EXECUTE '
            UPDATE party_applications
            SET applicant_rating = COALESCE(applicant_rating, applicantrating)
            WHERE applicant_rating IS NULL
        ';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'party_applications'
          AND column_name = 'depositamount'
    ) THEN
        EXECUTE '
            UPDATE party_applications
            SET deposit_amount = COALESCE(deposit_amount, depositamount)
            WHERE deposit_amount IS NULL
        ';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'party_applications'
          AND column_name = 'ispaid'
    ) THEN
        EXECUTE '
            UPDATE party_applications
            SET is_paid = COALESCE(is_paid, ispaid)
            WHERE is_paid IS NULL
        ';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'party_applications'
          AND column_name = 'isapproved'
    ) THEN
        EXECUTE '
            UPDATE party_applications
            SET is_approved = COALESCE(is_approved, isapproved)
            WHERE is_approved IS NULL
        ';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'party_applications'
          AND column_name = 'isrejected'
    ) THEN
        EXECUTE '
            UPDATE party_applications
            SET is_rejected = COALESCE(is_rejected, isrejected)
            WHERE is_rejected IS NULL
        ';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'party_applications'
          AND column_name = 'paymenttype'
    ) THEN
        EXECUTE '
            UPDATE party_applications
            SET payment_type = COALESCE(payment_type, paymenttype)
            WHERE payment_type IS NULL
        ';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'party_applications'
          AND column_name = 'createdat'
    ) THEN
        EXECUTE '
            UPDATE party_applications
            SET created_at = COALESCE(created_at, createdat)
            WHERE created_at IS NULL
        ';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'party_applications'
          AND column_name = 'approvedat'
    ) THEN
        EXECUTE '
            UPDATE party_applications
            SET approved_at = COALESCE(approved_at, approvedat)
            WHERE approved_at IS NULL
        ';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'party_applications'
          AND column_name = 'rejectedat'
    ) THEN
        EXECUTE '
            UPDATE party_applications
            SET rejected_at = COALESCE(rejected_at, rejectedat)
            WHERE rejected_at IS NULL
        ';
    END IF;
END;
$$;

UPDATE party_applications
SET applicant_name = COALESCE(applicant_name, 'UNKNOWN'),
    applicant_badge = COALESCE(applicant_badge, 'NEW'),
    applicant_rating = COALESCE(applicant_rating, 5.0),
    deposit_amount = COALESCE(deposit_amount, 0),
    is_paid = COALESCE(is_paid, FALSE),
    is_approved = COALESCE(is_approved, FALSE),
    is_rejected = COALESCE(is_rejected, FALSE),
    payment_type = COALESCE(payment_type, 'DEPOSIT'),
    created_at = COALESCE(created_at, NOW());

ALTER TABLE party_applications
    ALTER COLUMN applicant_name SET NOT NULL,
    ALTER COLUMN applicant_badge SET NOT NULL,
    ALTER COLUMN applicant_rating SET NOT NULL,
    ALTER COLUMN deposit_amount SET NOT NULL,
    ALTER COLUMN is_paid SET NOT NULL,
    ALTER COLUMN is_approved SET NOT NULL,
    ALTER COLUMN is_rejected SET NOT NULL,
    ALTER COLUMN payment_type SET NOT NULL,
    ALTER COLUMN created_at SET NOT NULL;

-- ticket_verifications: ensure table exists and consumed type matches entity (Boolean).
CREATE TABLE IF NOT EXISTS ticket_verifications (
    token VARCHAR(36) PRIMARY KEY,
    ticket_date VARCHAR(255),
    ticket_stadium VARCHAR(255),
    home_team VARCHAR(255),
    away_team VARCHAR(255),
    game_id BIGINT,
    consumed BOOLEAN NOT NULL DEFAULT FALSE,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'ticket_verifications'
          AND column_name = 'consumed'
          AND data_type <> 'boolean'
    ) THEN
        EXECUTE '
            ALTER TABLE ticket_verifications
            DROP CONSTRAINT IF EXISTS chk_ticket_verifications_consumed,
            ALTER COLUMN consumed DROP DEFAULT,
            ALTER COLUMN consumed TYPE BOOLEAN
            USING CASE
                WHEN consumed::text IN (''1'', ''true'', ''t'', ''TRUE'', ''T'') THEN TRUE
                ELSE FALSE
            END,
            ALTER COLUMN consumed SET DEFAULT FALSE
        ';
    END IF;
END;
$$;

CREATE INDEX IF NOT EXISTS idx_ticket_verifications_expires
    ON ticket_verifications (expires_at);
