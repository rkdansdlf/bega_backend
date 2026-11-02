-- Expand-only registry. Existing objects are not trusted until reference reconciliation completes.
CREATE TABLE physical_media_objects (
    object_hash VARCHAR2(64) PRIMARY KEY,
    physical_bucket VARCHAR2(255) NOT NULL,
    object_key VARCHAR2(2048) NOT NULL,
    state VARCHAR2(24) DEFAULT 'HOLD_UNVERIFIED' NOT NULL,
    audit_generation NUMBER(19) DEFAULT 0 NOT NULL,
    reference_revision NUMBER(19) DEFAULT 0 NOT NULL,
    claim_generation NUMBER(19) DEFAULT 0 NOT NULL,
    deletion_requested NUMBER(1) DEFAULT 0 NOT NULL,
    claim_token VARCHAR2(36),
    lease_until TIMESTAMP(6),
    retry_at TIMESTAMP(6),
    first_absent_at TIMESTAMP(6),
    first_absence_generation NUMBER(19) DEFAULT 0 NOT NULL,
    second_absent_at TIMESTAMP(6),
    attempt_count NUMBER(10) DEFAULT 0 NOT NULL,
    created_at TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT ck_pmo_hash_length CHECK (LENGTH(object_hash) = 64),
    CONSTRAINT ck_pmo_state CHECK (state IN ('HOLD_UNVERIFIED', 'ACTIVE', 'DELETE_PENDING', 'DELETE_CLAIMED', 'DELETED')),
    CONSTRAINT ck_pmo_generation CHECK (audit_generation BETWEEN 0 AND 9223372036854775807
        AND (state = 'HOLD_UNVERIFIED' OR audit_generation > 0)),
    CONSTRAINT ck_pmo_revisions CHECK (reference_revision BETWEEN 0 AND 9223372036854775807
        AND claim_generation BETWEEN 0 AND 9223372036854775807),
    CONSTRAINT ck_pmo_intent CHECK ((state = 'ACTIVE' AND deletion_requested = 0)
        OR (state IN ('DELETE_PENDING', 'DELETE_CLAIMED', 'DELETED') AND deletion_requested = 1)
        OR (state = 'HOLD_UNVERIFIED' AND deletion_requested IN (0, 1))),
    CONSTRAINT ck_pmo_claim CHECK ((state = 'DELETE_CLAIMED' AND claim_generation > 0 AND claim_token IS NOT NULL AND TRIM(claim_token) IS NOT NULL AND lease_until IS NOT NULL AND retry_at IS NULL)
        OR (state <> 'DELETE_CLAIMED' AND claim_token IS NULL AND lease_until IS NULL)),
    CONSTRAINT ck_pmo_retry CHECK (retry_at IS NULL OR (state IN ('HOLD_UNVERIFIED', 'DELETE_PENDING') AND deletion_requested = 1)),
    CONSTRAINT ck_pmo_absence CHECK (((first_absent_at IS NULL AND first_absence_generation = 0)
        OR (first_absent_at IS NOT NULL AND first_absence_generation > 0 AND first_absence_generation <= claim_generation
        AND deletion_requested = 1 AND state IN ('DELETE_PENDING', 'DELETE_CLAIMED', 'DELETED')
        AND (state <> 'DELETE_PENDING' OR (retry_at IS NOT NULL AND retry_at > first_absent_at))
        AND (state <> 'DELETE_CLAIMED' OR first_absence_generation < claim_generation)))
        AND ((state = 'DELETED' AND claim_generation >= 2 AND first_absent_at IS NOT NULL
            AND first_absence_generation < claim_generation AND second_absent_at IS NOT NULL AND second_absent_at > first_absent_at)
            OR (state <> 'DELETED' AND second_absent_at IS NULL))),
    CONSTRAINT ck_pmo_attempts CHECK (attempt_count BETWEEN 0 AND 2147483647)
);

CREATE TABLE physical_media_refs (
    source_kind VARCHAR2(32) NOT NULL,
    source_id VARCHAR2(128) NOT NULL,
    source_generation VARCHAR2(36) NOT NULL,
    object_hash VARCHAR2(64) NOT NULL,
    created_at TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_physical_media_refs PRIMARY KEY (source_kind, source_id, source_generation),
    CONSTRAINT fk_pmr_object FOREIGN KEY (object_hash) REFERENCES physical_media_objects (object_hash)
);

CREATE INDEX idx_pmo_cleanup ON physical_media_objects (state, retry_at, lease_until);
CREATE INDEX idx_pmr_object ON physical_media_refs (object_hash);
