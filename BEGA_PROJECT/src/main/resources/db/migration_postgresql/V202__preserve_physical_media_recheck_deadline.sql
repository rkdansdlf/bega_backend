ALTER TABLE physical_media_objects DROP CONSTRAINT ck_pmo_claim;
ALTER TABLE physical_media_objects DROP CONSTRAINT ck_pmo_retry;

ALTER TABLE physical_media_objects ADD CONSTRAINT ck_pmo_claim CHECK (
    (state = 'DELETE_CLAIMED' AND claim_generation > 0 AND claim_token IS NOT NULL
        AND TRIM(claim_token) <> '' AND lease_until IS NOT NULL)
    OR (state <> 'DELETE_CLAIMED' AND claim_token IS NULL AND lease_until IS NULL)
);
ALTER TABLE physical_media_objects ADD CONSTRAINT ck_pmo_retry CHECK (
    retry_at IS NULL OR (deletion_requested = TRUE AND
        (state IN ('HOLD_UNVERIFIED', 'DELETE_PENDING') OR
         (state = 'DELETE_CLAIMED' AND first_absent_at IS NOT NULL AND retry_at > first_absent_at)))
);
