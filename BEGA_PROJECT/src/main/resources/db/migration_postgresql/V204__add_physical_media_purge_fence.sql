ALTER TABLE physical_media_objects ADD COLUMN purge_fence_state VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN';
ALTER TABLE physical_media_objects ADD CONSTRAINT ck_pmo_purge_fence
    CHECK (purge_fence_state IN ('UNKNOWN', 'CLEAR', 'FENCED'));
