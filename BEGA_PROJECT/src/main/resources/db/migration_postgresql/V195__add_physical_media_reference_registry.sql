-- Expand-only registry. Existing objects are not trusted until reference reconciliation completes.
CREATE TABLE physical_media_objects (
    object_hash VARCHAR(64) PRIMARY KEY,
    physical_bucket VARCHAR(255) NOT NULL,
    object_key VARCHAR(2048) NOT NULL,
    state VARCHAR(24) NOT NULL DEFAULT 'HOLD_UNVERIFIED',
    audit_generation BIGINT NOT NULL DEFAULT 0,
    reference_revision BIGINT NOT NULL DEFAULT 0,
    claim_generation BIGINT NOT NULL DEFAULT 0,
    deletion_requested BOOLEAN NOT NULL DEFAULT FALSE,
    claim_token VARCHAR(36),
    lease_until TIMESTAMP,
    retry_at TIMESTAMP,
    first_absent_at TIMESTAMP,
    first_absence_generation BIGINT NOT NULL DEFAULT 0,
    second_absent_at TIMESTAMP,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_pmo_hash_length CHECK (LENGTH(object_hash) = 64),
    CONSTRAINT ck_pmo_state CHECK (state IN ('HOLD_UNVERIFIED', 'ACTIVE', 'DELETE_PENDING', 'DELETE_CLAIMED', 'DELETED')),
    CONSTRAINT ck_pmo_generation CHECK (audit_generation >= 0 AND (state = 'HOLD_UNVERIFIED' OR audit_generation > 0)),
    CONSTRAINT ck_pmo_revisions CHECK (reference_revision >= 0 AND claim_generation >= 0),
    CONSTRAINT ck_pmo_intent CHECK ((state = 'ACTIVE' AND deletion_requested = FALSE)
        OR (state IN ('DELETE_PENDING', 'DELETE_CLAIMED', 'DELETED') AND deletion_requested = TRUE)
        OR state = 'HOLD_UNVERIFIED'),
    CONSTRAINT ck_pmo_claim CHECK ((state = 'DELETE_CLAIMED' AND claim_generation > 0 AND claim_token IS NOT NULL AND TRIM(claim_token) <> '' AND lease_until IS NOT NULL AND retry_at IS NULL)
        OR (state <> 'DELETE_CLAIMED' AND claim_token IS NULL AND lease_until IS NULL)),
    CONSTRAINT ck_pmo_retry CHECK (retry_at IS NULL OR (state IN ('HOLD_UNVERIFIED', 'DELETE_PENDING') AND deletion_requested = TRUE)),
    CONSTRAINT ck_pmo_absence CHECK (((first_absent_at IS NULL AND first_absence_generation = 0)
        OR (first_absent_at IS NOT NULL AND first_absence_generation > 0 AND first_absence_generation <= claim_generation
        AND deletion_requested = TRUE AND state IN ('DELETE_PENDING', 'DELETE_CLAIMED', 'DELETED')
        AND (state <> 'DELETE_PENDING' OR (retry_at IS NOT NULL AND retry_at > first_absent_at))
        AND (state <> 'DELETE_CLAIMED' OR first_absence_generation < claim_generation)))
        AND ((state = 'DELETED' AND claim_generation >= 2 AND first_absent_at IS NOT NULL
            AND first_absence_generation < claim_generation AND second_absent_at IS NOT NULL AND second_absent_at > first_absent_at)
            OR (state <> 'DELETED' AND second_absent_at IS NULL))),
    CONSTRAINT ck_pmo_attempts CHECK (attempt_count >= 0)
);

CREATE TABLE physical_media_refs (
    source_kind VARCHAR(32) NOT NULL,
    source_id VARCHAR(128) NOT NULL,
    source_generation VARCHAR(36) NOT NULL,
    object_hash VARCHAR(64) NOT NULL REFERENCES physical_media_objects (object_hash),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (source_kind, source_id, source_generation)
);

CREATE INDEX idx_pmo_cleanup ON physical_media_objects (state, retry_at, lease_until);
CREATE INDEX idx_pmr_object ON physical_media_refs (object_hash);
